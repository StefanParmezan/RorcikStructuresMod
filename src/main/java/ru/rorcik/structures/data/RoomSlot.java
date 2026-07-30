package ru.rorcik.structures.data;

public class RoomSlot {
    private String nbt;
    private int[] position;
    private int[] offset;
    private int[] size;
    private int rotation;
    private int startFloor;
    private int repeat;
    private String variant;
    private int decorCount;
    private boolean mirrorX;
    private boolean mirrorZ;

    public RoomSlot() {
        this.mirrorX = false;
        this.mirrorZ = false;
    }

    public String nbt() { return nbt; }
    public int[] position() { return position; }
    public int[] offset() { return offset; }
    public int[] size() { return size; }
    public int rotation() { return rotation; }
    public int startFloor() { return startFloor; }
    public int repeat() { return repeat; }
    public String variant() { return variant; }
    public int decorCount() { return decorCount; }
    public boolean mirrorX() { return mirrorX; }
    public boolean mirrorZ() { return mirrorZ; }

    public void setDecorCount(int decorCount) {
        this.decorCount = decorCount;
    }
}