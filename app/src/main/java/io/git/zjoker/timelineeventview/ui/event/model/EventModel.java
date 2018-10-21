package io.git.zjoker.timelineeventview.ui.event.model;

public class EventModel {
    public static final int STATUS_NORMAL = 0;
    public static final int STATUS_EDITING = 1;
    public static final int STATUS_SCALING_TOP = 2;
    public static final int STATUS_SCALING_BOTTOM = 3;


    public long timeStart;
    public long timeTaken;
    public int status = STATUS_NORMAL;


    public EventModel(long timeStart, long timeTaken) {
        this.timeStart = timeStart;
        this.timeTaken = timeTaken;
    }

    public EventModel(long timeStart, long timeTaken, int status) {
        this.timeStart = timeStart;
        this.timeTaken = timeTaken;
        this.status = status;
    }


    public void changeToNormal() {
        this.status = STATUS_NORMAL;
    }

    public void changeToEdit() {
        this.status = STATUS_EDITING;
    }

    public void changeToScaleTop() {
        this.status = STATUS_SCALING_TOP;
    }

    public void changeToScaleBottom() {
        this.status = STATUS_SCALING_BOTTOM;
    }

    public void moveTo(long newTimeStart) {
        this.timeStart = newTimeStart;
    }

    public void scaleTopTo(long newTimeStart) {
        long diff = this.timeStart - newTimeStart ;
        moveTo(newTimeStart);
        timeTaken += diff;
    }

    public void scaleBottomTo(long newTimeEnd) {
        timeTaken = newTimeEnd - timeStart;
    }
}