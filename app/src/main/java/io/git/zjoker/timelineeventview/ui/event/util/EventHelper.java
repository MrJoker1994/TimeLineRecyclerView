package io.git.zjoker.timelineeventview.ui.event.util;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import io.git.zjoker.timelineeventview.ui.event.model.Event;
import io.git.zjoker.timelineeventview.ui.event.model.EventNode;
import io.git.zjoker.timelineeventview.ui.widget.TimeLineEventView;
import io.git.zjoker.timelineeventview.util.ViewUtil;

import static io.git.zjoker.timelineeventview.ui.event.model.Event.STATUS_EDITING;
import static io.git.zjoker.timelineeventview.ui.event.model.Event.STATUS_NORMAL;
import static io.git.zjoker.timelineeventview.ui.event.model.Event.STATUS_SCALING_TOP;

public class EventHelper {
    private List<Event> events;
    private WeakReference<TimeLineEventView> timeLineEventViewWR;
    private Paint eventSolidP;
    private Paint eventEditP;
    private Paint eventDragHandlerP;
    private TextPaint eventContentP;
    private GestureDetector gestureDetector;
    public static final long DEFAULT_EVENT_TIME_TAKEN = 50 * 60 * 1000;

    private int editingPostion = -1;
    private float dragHandlerRadius;
    private EventAdjustListener eventAdjustListener;
    private boolean hasEventUnderTouch;

    private float moveDistanceY;
    private float moveDistanceYInScroll;//滚动的时候用于判断move距离的，因为滚动的时候如果手指不动需要把move距离置为0
    private float lastTouchY;

    private ValueAnimator scrollAnimator;
    private EventNode eventEditing;
    private float eventPadding;

    public EventHelper() {
        this.events = new ArrayList<>();
        eventSolidP = new Paint();
        eventSolidP.setStyle(Paint.Style.FILL);
        eventSolidP.setColor(Color.parseColor("#AA9999AA"));

        eventEditP = new Paint();
        eventEditP.setStyle(Paint.Style.FILL);
        eventEditP.setColor(Color.parseColor("#FFAAAAFF"));

        eventDragHandlerP = new Paint();
        eventDragHandlerP.setStyle(Paint.Style.STROKE);
        eventDragHandlerP.setStrokeWidth(ViewUtil.dpToPx(2));
        eventDragHandlerP.setColor(Color.parseColor("#FFAAAAFF"));


        eventContentP = new TextPaint();
        eventContentP.setColor(Color.WHITE);
        eventContentP.setTextSize(ViewUtil.spToPx(15));
        eventContentP.setStyle(Paint.Style.FILL);

        dragHandlerRadius = ViewUtil.dpToPx(15);

        eventPadding = ViewUtil.dpToPx(8);
    }

    public void setEventAdjustListener(EventAdjustListener eventAdjustListener) {
        this.eventAdjustListener = eventAdjustListener;
    }

    private EventNode buildEventTree() {
        List<Event> tmpEvent = new ArrayList<>(events);
        Collections.sort(tmpEvent, eventComparator);

        EventNode rootNode = new EventNode();
        if (tmpEvent.size() == 0) {
            return rootNode;
        }

        if (tmpEvent.size() == 1) {
            rootNode.appendChildNode(new EventNode(tmpEvent.get(0), 1));
        } else {
            List<EventNode> nodes = new ArrayList<>(tmpEvent.size());
            for (int i = 0; i < tmpEvent.size(); i++) {
                nodes.add(new EventNode(tmpEvent.get(i), 1));
            }

            for (int i = 0; i < nodes.size(); i++) {
                EventNode node1 = nodes.get(i);//正在找寻parent的node
                RectF rect1 = getV().getRectOnTimeLine(node1.event.timeStart, node1.event.timeTaken);
                int maxLevel = 0;
                EventNode parentNode = null;
                boolean hasSameNode = false;
                for (int j = 0; j < i; j++) {//和i之前的node挨个做对比，找出自己的所在位置
                    EventNode node2 = nodes.get(j);//前面已经构建好的node
                    RectF rect2 = getV().getRectOnTimeLine(node2.event.timeStart, node2.event.timeTaken);
                    if (rect1.equals(rect2)) {//完全重叠
                        hasSameNode = true;
                        node2.appendSameNode(node1);
                        break;
                    } else if (RectF.intersects(rect1, rect2)) {//有重叠
                        if (node2.level > maxLevel) {//找到深度最大的那个作为父节点
                            maxLevel = node2.level;
                            parentNode = node2;
                        }
                    }
                }
                if (parentNode != null) {
                    parentNode.appendChildNode(node1);
                    Log.d("ParentNode", parentNode.childNodes.toString());
                } else if (!hasSameNode) {//如果没有找到自己的位置，就属于是第一级
                    rootNode.appendChildNode(node1);
                }

            }
        }
        Log.d("EventNode", rootNode.toString());
        return rootNode;
    }

    public void attach(TimeLineEventView timeLineEventView) {
        timeLineEventViewWR = new WeakReference<>(timeLineEventView);
        gestureDetector = new GestureDetector(timeLineEventView.getContext(), new GestureDetector.SimpleOnGestureListener() {

            @Override
            public void onLongPress(MotionEvent e) {
                super.onLongPress(e);
                float touchX = e.getX();
                float touchY = getYWithScroll(e.getY());

                if (getEventEditing() == null) {
                    Event eventUnderTouch = getEventUnderTouch(touchX, touchY);
                    if (eventUnderTouch == null) {
                        eventUnderTouch = createEvent(e.getY());
                        eventUnderTouch.changeToEdit();
                        events.add(eventUnderTouch);
                    } else {
                        eventUnderTouch.changeToEdit();
                    }
                    hasEventUnderTouch = true;
                    if (eventAdjustListener != null) {
                        eventAdjustListener.onEventAdjusting(eventUnderTouch.timeStart);
                    }
                    invalidate();
                }
            }

            @Override
            public boolean onDown(MotionEvent e) {
                float touchX = e.getX();
                float touchY = getYWithScroll(e.getY());
                Event eventEditing = getEventEditing();
                if (eventEditing != null) {
                    if (isTopScalerUnderTouch(eventEditing, touchX, touchY)) {
                        eventEditing.changeToScaleTop();
                        hasEventUnderTouch = true;
                    } else if (isBottomScalerUnderTouch(eventEditing, touchX, touchY)) {
                        eventEditing.changeToScaleBottom();
                        hasEventUnderTouch = true;
                    } else if (isEventUnderTouch(eventEditing, touchX, touchY)) {
                        eventEditing.changeToEdit();
                        hasEventUnderTouch = true;
                    } else {
                        hasEventUnderTouch = false;
                    }
                } else {
                    hasEventUnderTouch = false;
                }
                return hasEventUnderTouch;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                boolean hasEditingEvent = getEventEditing() != null;
                if (hasEditingEvent && !hasEventUnderTouch) {
                    resetEventStatus();
                    if (eventAdjustListener != null) {
                        eventAdjustListener.onEventAdjustEnd();
                    }
                    invalidate();
                }
                return super.onSingleTapUp(e);
            }
        });
    }

    private float getYWithScroll(float touchY) {
        return touchY + getV().getScrollY();
    }

    private boolean isEventUnderTouch(Event eventModel, float touchX, float touchY) {
        RectF rectF = getV().getRectOnTimeLine(eventModel.timeStart, eventModel.timeTaken);
        return rectF.contains(touchX, touchY);
    }

    private void invalidate() {
        getV().invalidate();
    }

    private Event getEventUnderTouch(float touchX, float touchY) {
        for (int i = 0; i < events.size(); i++) {
            Event eventModel = events.get(i);
            if (isEventUnderTouch(eventModel, touchX, touchY)) {
                return eventModel;
            }
        }
        return null;
    }

    private boolean isTopScalerUnderTouch(Event editingEvent, float touchX, float touchY) {
        RectF rectF = getV().getRectOnTimeLine(editingEvent.timeStart, editingEvent.timeTaken);

        PointF topDragHandlerPoint = getTopScallerPoint(rectF);
        return getScalerRectF(topDragHandlerPoint.x, topDragHandlerPoint.y).contains(touchX, touchY);
    }

    private boolean isBottomScalerUnderTouch(Event editingEvent, float touchX, float touchY) {
        RectF rectF = getV().getRectOnTimeLine(editingEvent.timeStart, editingEvent.timeTaken);
        PointF topDragHandlerPoint = getBottomScallerPoint(rectF);
        return getScalerRectF(topDragHandlerPoint.x, topDragHandlerPoint.y).contains(touchX, touchY);
    }

    private boolean isInRect(RectF rectF, float x, float y) {
        return rectF.contains(x, getYWithScroll(y));
    }

    private RectF getScalerRectF(float x, float y) {
        return new RectF(x - dragHandlerRadius, y - dragHandlerRadius, x + dragHandlerRadius, y + dragHandlerRadius);
    }


    private Event createEvent(float touchY) {
        long timeStart = getV().getTimeByOffsetY(touchY);
        return new Event(timeStart, DEFAULT_EVENT_TIME_TAKEN, STATUS_EDITING);
    }

    public boolean onTouchEvent(MotionEvent motionEvent) {
        gestureDetector.onTouchEvent(motionEvent);
        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchY = motionEvent.getY();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                moveDistanceYInScroll = moveDistanceY = 0;
                stopScroll();
                if (hasEventUnderTouch && eventAdjustListener != null) {
                    eventAdjustListener.onEventAdjustEnd();
                }
                break;
            case MotionEvent.ACTION_MOVE:
                moveDistanceYInScroll = moveDistanceY = motionEvent.getY() - lastTouchY;

                Log.d("checkScroll", String.valueOf(moveDistanceY));
                lastTouchY = motionEvent.getY();
                if (hasEventUnderTouch) {
                    if (!checkScroll(lastTouchY)) {
                        checkEditEvent(motionEvent.getX(), lastTouchY, moveDistanceY);
                    }
                    return true;
                }
                break;
        }
        return hasEventUnderTouch;
    }

    private boolean checkEditEvent(float touchX, float touchY, float moveDistanceY) {
        Event eventEditing = getEventEditing();
        if (moveDistanceY != 0 && eventEditing != null) {
            long timeAdjust = getV().getTimeByDistance(moveDistanceY);
            Log.d("checkEditEvent", timeAdjust + "--" + moveDistanceY);
            long timeAdjustBound;
            if (eventEditing.status == STATUS_EDITING) {
                eventEditing.moveBy(timeAdjust);
                timeAdjustBound = eventEditing.timeStart;
            } else if (eventEditing.status == STATUS_SCALING_TOP) {
                eventEditing.scaleTopBy(timeAdjust);
                if (DEFAULT_EVENT_TIME_TAKEN > eventEditing.timeTaken) {
                    eventEditing.scaleTopBy(eventEditing.timeTaken - DEFAULT_EVENT_TIME_TAKEN);
                }
                timeAdjustBound = eventEditing.timeStart;
            } else {
                eventEditing.scaleBottomBy(timeAdjust);
                if (DEFAULT_EVENT_TIME_TAKEN > eventEditing.timeTaken) {
                    eventEditing.scaleBottomBy(DEFAULT_EVENT_TIME_TAKEN - eventEditing.timeTaken);
                }
                timeAdjustBound = eventEditing.getTimeEnd();
            }

            invalidate();
            if (eventAdjustListener != null) {
                eventAdjustListener.onEventAdjusting(timeAdjustBound);
            }
            return true;
        }
        return false;
    }

    private boolean checkScroll(float touchY) {
        int adjustSpace = getV().getHeight() / 8;
        if (touchY < adjustSpace && moveDistanceYInScroll <= 0 && getV().canScroll(false)) {
            startScroll(false);
            return true;
        } else if (touchY > getV().getHeight() - adjustSpace && moveDistanceYInScroll >= 0 && getV().canScroll(true)) {
            startScroll(true);
            return true;
        } else {
            stopScroll();
        }
        return false;
    }

    private void startScroll(final boolean isScrollUp) {
        if (scrollAnimator != null && scrollAnimator.isRunning()) {
            return;
        }

        final int from;
        int target;
        if (isScrollUp) {
            from = getV().getScrollY();
            target = getV().getTotalHeight() - getV().getHeight();
        } else {
            from = getV().getScrollY();
            target = 0;
        }
        moveDistanceYInScroll = 0;
        scrollAnimator = ObjectAnimator.ofInt(from, target).setDuration(Math.abs(target - from));
        scrollAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            private int lastScrollBy = from;

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int scrollTo = (int) animation.getAnimatedValue();
                if (eventAdjustListener != null) {
                    eventAdjustListener.onEventAdjustWithScroll(scrollTo);
                }
//                Log.d("onAnimationUpdate", String.valueOf(moveDistanceY));
                checkEditEvent(0, lastTouchY, scrollTo - lastScrollBy + moveDistanceYInScroll);//滚动距离+滚动时的move距离
                lastScrollBy = scrollTo;
                moveDistanceYInScroll = 0;
            }
        });
        scrollAnimator.start();
    }

    private void stopScroll() {
        if (scrollAnimator != null) {
            scrollAnimator.cancel();
        }
    }

    private Event getEventEditing() {
        for (int i = 0; i < events.size(); i++) {
            Event eventModel = events.get(i);
            if (eventModel.status != STATUS_NORMAL) {
                return eventModel;
            }
        }
        return null;
    }

    public void draw(Canvas canvas) {
        EventNode eventNode = buildEventTree();
        if (eventNode.childNodes.size() == 0) {
            return;
        }
        drawEvents(eventNode.childNodes, canvas);
    }

    private void resetEventStatus() {
        for (int i = 0; i < events.size(); i++) {
            events.get(i).changeToNormal();
        }
    }

    private void drawEvents(List<EventNode> nodes, Canvas canvas) {
        for (int i = 0; i < nodes.size(); i++) {
            EventNode eventNode = nodes.get(i);
            drawSingleEvent(eventNode, canvas);
            drawEvents(eventNode.childNodes, canvas);
        }
    }

    private void drawSingleEvent(EventNode node, Canvas canvas) {
        RectF rectF = getV().getRectOnTimeLine(node.event.timeStart, node.event.timeTaken);
        if (node.event.status != STATUS_NORMAL) {
            drawEventOnEdit(canvas, node.event, rectF);
            if (!node.sameNodes.isEmpty()) {
                float rectWidth = rectF.width() / (node.sameNodes.size());
                RectF sameNodeRect = new RectF(rectF.left, rectF.top, rectWidth, rectF.top);
                for (int i = 0; i < node.sameNodes.size(); i++) {
                    sameNodeRect.offsetTo(i * rectWidth, rectF.top);
                    drawEventOnNormal(canvas, node.sameNodes.get(0).event, sameNodeRect);
                }
            }
        } else {
            rectF.left += (node.level - 1) * 10;
            if (node.sameNodes.isEmpty()) {
                drawEventOnNormal(canvas, node.event, rectF);
            } else {
                float rectWidth = rectF.width() / (node.sameNodes.size() + 1);
                RectF sameNodeRect = new RectF(rectF.left, rectF.top, rectF.left + rectWidth, rectF.bottom);
                drawEventOnNormal(canvas, node.event, sameNodeRect);
                for (int i = 0; i < node.sameNodes.size(); i++) {
                    sameNodeRect.offset(rectWidth, 0);
                    drawEventOnNormal(canvas, node.sameNodes.get(0).event, sameNodeRect);
                }
            }
        }
    }

    private void drawEventOnNormal(Canvas canvas, Event event, RectF rectF) {
        canvas.drawRect(rectF, eventSolidP);
        canvas.drawRect(rectF.left, rectF.top, rectF.left + ViewUtil.dpToPx(3), rectF.bottom, eventEditP);
        drawContent(canvas, event, rectF);
    }

    private void drawEventOnEdit(Canvas canvas, Event event, RectF rectF) {
        canvas.drawRect(rectF, eventEditP);
        drawContent(canvas, event, rectF);

        PointF topDragHandlerPoint = getTopScallerPoint(rectF);
        canvas.drawCircle(topDragHandlerPoint.x, topDragHandlerPoint.y, dragHandlerRadius, eventDragHandlerP);

        PointF bottomDragHandlerPoint = getBottomScallerPoint(rectF);
        canvas.drawCircle(bottomDragHandlerPoint.x, bottomDragHandlerPoint.y, dragHandlerRadius, eventDragHandlerP);

    }

    private void drawContent(Canvas canvas, Event event, RectF rectF) {
        int save = canvas.save();
        canvas.translate(rectF.left + eventPadding, rectF.top + eventPadding);
        int width = (int) (rectF.width() - 2 * eventPadding);
        StaticLayout myStaticLayout = new StaticLayout(event.text, 0, event.text.length(),
                eventContentP, width,
                Layout.Alignment.ALIGN_NORMAL,
                1.0f, 0.0f,
                false,
                null, width);
        myStaticLayout.draw(canvas);
        canvas.restoreToCount(save);
    }

    private PointF getTopScallerPoint(RectF eventRectF) {
        float size = 2 * dragHandlerRadius;
        float topXOffset = eventRectF.right - size;
        float topYOffset = eventRectF.top;
        return new PointF(topXOffset, topYOffset);
    }

    private PointF getBottomScallerPoint(RectF eventRectF) {
        float size = 2 * dragHandlerRadius;
        float bottomXOffset = eventRectF.left + size;
        float bottomYOffset = eventRectF.bottom;
        return new PointF(bottomXOffset, bottomYOffset);
    }

    public TimeLineEventView getV() {
        return timeLineEventViewWR.get();
    }

    public interface EventAdjustListener {
        void onEventAdjusting(long timeAdjust);

        void onEventAdjustEnd();

        void onEventAdjustWithScroll(int scrollTo);
    }

    public void dettach() {
        if (scrollAnimator != null) {
            scrollAnimator.cancel();
        }
        if (timeLineEventViewWR != null) {
            timeLineEventViewWR.clear();
        }
    }

    private Comparator<Event> eventComparator = new Comparator<Event>() {
        @Override
        public int compare(Event o1, Event o2) {
            return (int) (o1.timeStart - o2.timeStart);
        }
    };
}
