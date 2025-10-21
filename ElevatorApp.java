import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ElevatorApp {

    enum Direction { UP, DOWN, IDLE }

    //this class represents a passanger request from origin to destination
    static final class Request {
        final int origin;
        final int destination;
        final long createdAtTick;

        Request(int origin, int destination, long createdAtTick) {
            if (origin == destination) {
                throw new IllegalArgumentException("origin and destination must differ");
            }
            this.origin = origin;
            this.destination = destination;
            this.createdAtTick = createdAtTick;
        }

        //determine whether the request is going up or down
        Direction desiredDirection() {
            return destination > origin ? Direction.UP : Direction.DOWN;
        }

        @Override
        public String toString() {
            return "Request{" + origin + "->" + destination + "}";
        }
    }

    //this class represents an elevator car
    static final class Elevator {
        private final int id;
        private final int minFloor;
        private final int maxFloor;
        private final TreeSet<Integer> upStops = new TreeSet<>();
        private final TreeSet<Integer> downStops = new TreeSet<>(Comparator.reverseOrder());
        private int currentFloor;
        private Direction direction = Direction.IDLE;

        private int doorHoldTicksRemaining = 0;
        private final int DOOR_OPEN_TICKS = 2;
        private final int CAPACITY = 12; 

        //track the onboard destinations and their counts 
        private final Map<Integer, Integer> onboardCountsPerDestination = new HashMap<>();

        Elevator(int id, int startFloor, int minFloor, int maxFloor) {
            this.id = id;
            this.currentFloor = startFloor;
            this.minFloor = minFloor;
            this.maxFloor = maxFloor;
        }

        int id() { return id; }
        int currentFloor() { return currentFloor; }
        Direction direction() { return direction; }
        boolean isIdle() { return direction == Direction.IDLE && upStops.isEmpty() && downStops.isEmpty() && doorHoldTicksRemaining == 0; }

        //add a stop to the queue
        void addStop(int floor) {
            if (floor < minFloor || floor > maxFloor) return;
            if (floor == currentFloor && doorHoldTicksRemaining == 0) {
                //Arrive immediately this tick (handled in step())
                upStops.add(floor); //add to one set so it gets served; will be removed immediately
                return;
            }
            if (direction == Direction.UP) {
                if (floor >= currentFloor) upStops.add(floor); else downStops.add(floor);
            } else if (direction == Direction.DOWN) {
                if (floor <= currentFloor) downStops.add(floor); else upStops.add(floor);
            } else { //set the elevator to idle
                if (floor >= currentFloor) upStops.add(floor); else downStops.add(floor);
            }
        }

        //add a destination after pickup
        void addDestination(int dest) {
            addStop(dest);
            onboardCountsPerDestination.put(dest, onboardCountsPerDestination.getOrDefault(dest, 0) + 1);
        }

        //if the doors are open, we are going to wait until they close
        //otherwise, we move the elevator one floor in the current direction
        void step(Controller controller, long tick) {
            if (doorHoldTicksRemaining > 0) {
                doorHoldTicksRemaining--;
                if (doorHoldTicksRemaining == 0) {
                    //potetnially update direction once the door closes 
                    updateDirection();
                }
                return;
            }

            //stop if current floor is in any set
            if (upStops.contains(currentFloor) || downStops.contains(currentFloor)) {
                serveCurrentFloor(controller, tick);
                return;
            }

            //Decide direction if idle but have work
            if (direction == Direction.IDLE) updateDirection();

            //move a floor in the current direction 
            if (direction == Direction.UP) {
                if (!upStops.isEmpty()) currentFloor++;
                else if (!downStops.isEmpty()) currentFloor--; //edge case,
            } else if (direction == Direction.DOWN) {
                if (!downStops.isEmpty()) currentFloor--;
                else if (!upStops.isEmpty()) currentFloor++; //edge case, 
            }

            //clamp to min/max floors
            if (currentFloor < minFloor) currentFloor = minFloor;
            if (currentFloor > maxFloor) currentFloor = maxFloor;

        }

        private void serveCurrentFloor(Controller controller, long tick) {
            boolean servingUp = upStops.contains(currentFloor);
            boolean servingDown = downStops.contains(currentFloor);

            if (servingUp) upStops.remove(currentFloor);
            if (servingDown) downStops.remove(currentFloor);

            //drop offs, if we need to
            int dropped = onboardCountsPerDestination.getOrDefault(currentFloor, 0);
            if (dropped > 0) {
                onboardCountsPerDestination.remove(currentFloor);
            }

            //board waiting passengers at this floor
            int boarded = controller.boardWaitingPassengersAt(this, currentFloor, tick);

            //open doors if anyone got on or off
            if (dropped > 0 || boarded > 0) {
                doorHoldTicksRemaining = DOOR_OPEN_TICKS;
            }

            updateDirection();
        }

        //choose the next direction
        private void updateDirection() {
            if (!upStops.isEmpty() && !downStops.isEmpty()) {
                //prefer the nearest stop
                int nearestUp = upStops.ceiling(currentFloor) != null ? upStops.ceiling(currentFloor) : upStops.first();
                int nearestDown = downStops.floor(currentFloor) != null ? downStops.floor(currentFloor) : downStops.first();
                int upDist = Math.abs(nearestUp - currentFloor);
                int downDist = Math.abs(nearestDown - currentFloor);
                direction = (upDist <= downDist) ? Direction.UP : Direction.DOWN;
            } else if (!upStops.isEmpty()) {
                direction = Direction.UP;
            } else if (!downStops.isEmpty()) {
                direction = Direction.DOWN;
            } else {
                direction = Direction.IDLE;
            }
        }

        @Override
        public String toString() {
            return "Elevator{" +
                    "id=" + id +
                    ", floor=" + currentFloor +
                    ", dir=" + direction +
                    ", up=" + upStops +
                    ", down=" + downStops +
                    ", door=" + (doorHoldTicksRemaining > 0 ? "OPEN(" + doorHoldTicksRemaining + ")" : "CLOSED") +
                    '}';
        }
    }

   //this class represents the elevator controller managing multiple elevators
   //and handling requests
   static final class Controller {
        private final List<Elevator> fleet;
        private final int minFloor;
        private final int maxFloor;

        //separate queues for unassigned requests and waiting requests by floor
        private final Queue<Request> unassigned = new ArrayDeque<>();
        private final Map<Integer, Deque<Request>> waitingByFloor = new HashMap<>();

        Controller(int elevatorCount, int minFloor, int maxFloor, int startingFloor) {
            this.minFloor = minFloor;
            this.maxFloor = maxFloor;
            this.fleet = new ArrayList<>(elevatorCount);
            for (int i = 0; i < elevatorCount; i++) {
                fleet.add(new Elevator(i, startingFloor, minFloor, maxFloor));
            }
        }

        void submit(Request r) {
            if (r.origin < minFloor || r.origin > maxFloor || r.destination < minFloor || r.destination > maxFloor) {
                throw new IllegalArgumentException("Floor out of range: " + r);
            }
            // requests begin unassigned so the dispatcher can pick a car
            unassigned.offer(r);
        }

        //assign waiting requests to elevators based on a cost heuristic
        private void dispatchWaitingRequests(long tick) {
            //limit number of polls per tick to avoid spikes
            int polls = Math.min(unassigned.size(), 16);
            List<Request> deferred = new ArrayList<>();
            for (int i = 0; i < polls; i++) {
                Request r = unassigned.poll();
                if (r == null) break;

                Elevator best = chooseBestElevator(r);
                if (best == null) {
                    deferred.add(r); //there isn't a good match with this tick right now
                } else {
                    //assign to the best elevator
                    waitingByFloor.computeIfAbsent(r.origin, k -> new ArrayDeque<>()).offerLast(r);
                    best.addStop(r.origin);
                }
            }
            //re-add deferred requests
            deferred.forEach(unassigned::offer);
        }

        //choose the best elevator for a given request
        private Elevator chooseBestElevator(Request r) {
            Elevator best = null;
            int bestScore = Integer.MAX_VALUE;

            for (Elevator e : fleet) {
                int score = score(e, r);
                if (score < bestScore) {
                    bestScore = score;
                    best = e;
                }
            }
            return bestScore == Integer.MAX_VALUE ? null : best;
        }

        private int score(Elevator e, Request r) {
            int dist = Math.abs(e.currentFloor() - r.origin);

            //idle elevators are preferred
            if (e.direction() == Direction.IDLE) return dist;

            //moving towards the request in the same direction is good
            if (e.direction() == r.desiredDirection()) {
                if ((e.direction() == Direction.UP && e.currentFloor() <= r.origin) ||
                    (e.direction() == Direction.DOWN && e.currentFloor() >= r.origin)) {
                    return dist + 2; //little bit of bias
                }
            }

            //moving away or in opposite direction is bad
            return dist + 50;
        }

        
        //board waiting passengers at the given floor
        int boardWaitingPassengersAt(Elevator e, int floor, long tick) {
            Deque<Request> q = waitingByFloor.get(floor);
            if (q == null || q.isEmpty()) return 0;
            int boarded = 0;
            while (!q.isEmpty()) {
                Request r = q.pollFirst();
                e.addDestination(r.destination);
                boarded++;
            }
            return boarded;
        }

        //advance the simulation by one tick
        String tick(long tick) {
            dispatchWaitingRequests(tick);
            for (Elevator e : fleet) {
                e.step(this, tick);
            }
            return snapshot(tick);
        }

        String snapshot(long tick) {
            StringBuilder sb = new StringBuilder();
            int waitingUnassigned = unassigned.size();
            int waitingAtFloors = waitingByFloor.values().stream().mapToInt(Deque::size).sum();
            sb.append("T=").append(tick).append(" | waiting=").append(waitingUnassigned + waitingAtFloors).append("\n");
            for (Elevator e : fleet) {
                sb.append("  ").append(e).append("\n");
            }
            return sb.toString();
        }
    }


    public static void main(String[] args) {
        int minFloor = 1, maxFloor = 30;
        int elevators = 3;
        int startingFloor = 1;
        long totalTicks = 120; //this is 2 minutes of simulation time since each tick is 1 second

        Controller controller = new Controller(elevators, minFloor, maxFloor, startingFloor);

        List<Request> scripted = List.of(
                new Request(1, 18, 0),
                new Request(4, 2, 1),
                new Request(16, 27, 5),
                new Request(27, 3, 7),
                new Request(8, 22, 10),
                new Request(22, 5, 12),
                new Request(3, 30, 20),
                new Request(15, 1, 25),
                new Request(12, 25, 40),
                new Request(25, 11, 45)
        );

        //event loop starts here
        long tick = 0;
        int nextScriptedIndex = 0;
        while (tick <= totalTicks) {
            //submit any new scripted requests for this tick
            while (nextScriptedIndex < scripted.size() &&
                   scripted.get(nextScriptedIndex).createdAtTick <= tick) {
                controller.submit(scripted.get(nextScriptedIndex++));
            }

            //advance a tick
            String snapshot = controller.tick(tick);

            //print every 2 ticks
            if (tick % 2 == 0) {
                System.out.print(snapshot);
            }


            tick++;
        }

        //completed simulation
        System.out.println("Simulation complete.");
    }
}
