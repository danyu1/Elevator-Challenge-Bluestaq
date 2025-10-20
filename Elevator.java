public class Elevator {
    private int currentFloor;
    private final int minFloor;
    private final int maxFloor;

    public Elevator(int minFloor, int maxFloor) {
        this.minFloor = minFloor;
        this.maxFloor = maxFloor;
        this.currentFloor = 0; // Assuming ground floor is 0
    }

    public int getCurrentFloor() {
        return currentFloor;
    }

    public void moveToFloor(int targetFloor) {
        if (targetFloor < minFloor || targetFloor > maxFloor) {
            throw new IllegalArgumentException("Target floor is out of bounds.");
        }
        currentFloor = targetFloor;
    }
}