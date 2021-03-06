package org.nca.elevator;

import java.util.LinkedList;

import org.nca.elevator.strategy.ElevatorStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Elevator implements ElevatorState, ElevatorController {

  static final Logger logger = LoggerFactory.getLogger(Elevator.class);

  public static final String NOTHING = "NOTHING";
  public static final String UP = "UP";
  public static final String DOWN = "DOWN";
  public static final String OPEN = "OPEN";
  public static final String CLOSE = "CLOSE";

  public static final int MAX_FLOOR = 5;

  private int currentFloor;
  private Direction currentDirection;
  private Door doorState;
  private WaitingUsers waitingUsers;
  private ElevatorUsers elevatorUsers;
  private CommandHistory commandHistory;

  private ElevatorStrategy strategy;

  public Elevator(ElevatorStrategy strategy) {
    logger.info("Initialising elevator with strategy {}", strategy.getClass());
    this.strategy = strategy;
    resetState();
  }

  public static enum Command {
    NOTHING, OPEN, CLOSE, UP, DOWN;
  }

  static enum Direction {
    UP, DOWN, UNKNOWN;

    public Direction flip() {
      if (this == UNKNOWN) {
        throw new RuntimeException("Unable to flip this direction: " + this);
      }
      return this == DOWN ? UP : DOWN;
    }

    public Command toCommand() {
      if (this == UNKNOWN) {
        throw new RuntimeException("Unable to transform this direction into command: " + this);
      }
      return Command.valueOf(this.toString());
    }
  }

  static enum Door {
    OPEN, CLOSED;
  }

  static class CommandHistory {
    private LinkedList<Command> history = new LinkedList<Command>();
    int counter = 0;

    public void add(Command command) {
      // keep only 100 last items
      history.addFirst(command);
      counter++;
      if (counter >= 100) {
        history.removeLast();
      }
    }

    public boolean isStaleSince(int numberOfCommands) {
      if (counter <= numberOfCommands) {
        // not enough commands to look at
        return false;
      }
      for (int i = 0; i < numberOfCommands; i++) {
        Command command = history.get(i);
        if (!(command.equals(Command.NOTHING) || command.equals(Command.CLOSE))) {
          return false;
        }
      }
      return true;
    }
  }

  private void resetState() {
    currentFloor = 0;
    doorState = Door.CLOSED;
    currentDirection = Direction.UP;
    commandHistory = new CommandHistory();
    waitingUsers = new WaitingUsers();
    elevatorUsers = new ElevatorUsers();
  }

  void setStrategy(ElevatorStrategy newStrategy) {
    logger.info("--- Changing strategy to {} ---", newStrategy);
    this.strategy = newStrategy;
  }

  Class<? extends ElevatorStrategy> getStrategy() {
    return this.strategy.getClass();
  }

  public void reset() {
    resetState();
  }

  // floor: 0-5, to : UP/DOWN
  public void call(int atFloor, String to) {
    waitingUsers.add(new WaitingUser(atFloor, Direction.valueOf(to)));
  }

  public void go(int floor) {
    elevatorUsers.userRequestedFloor(floor, currentFloor);
  }

  public void userHasEntered() {
      WaitingUser user = waitingUsers.popUser(currentFloor);
      elevatorUsers.userEntered(user);
      logger.info("User has entered, added " + user);
  }

  public void userHasExited() {
    elevatorUsers.userExited(currentFloor);
  }

  public Command nextCommand() {
    ajustDirection();
    Command command = strategy.nextCommand(this, this);
    recordCommand(command);
    logger.info("Command: {}, state: {}", command, this);
    return command;
  }

  private void ajustDirection() {
    if (currentFloor == 0) {
      currentDirection = Direction.UP;
    }
    else if (currentFloor == MAX_FLOOR) {
      currentDirection = Direction.DOWN;
    }
  }

  private void recordCommand(Command command) {
    commandHistory.add(command);
  }

  /* (non-Javadoc)
   * @see org.nca.elevator.ElevatorState#hasDoorClosed()
   */
  @Override
  public boolean hasDoorClosed() {
    return doorState == Door.CLOSED;
  }

  /* (non-Javadoc)
   * @see org.nca.elevator.ElevatorState#hasDoorOpen()
   */
  @Override
  public boolean hasDoorOpen() {
    return doorState == Door.OPEN;
  }

  /* (non-Javadoc)
   * @see org.nca.elevator.ElevatorState#hasWaitingUserForCurrentFloor()
   */
  @Override
  public boolean hasWaitingUserForCurrentFloor() {
    return waitingUsers.hasUserFor(currentFloor);
  }

  /* (non-Javadoc)
   * @see org.nca.elevator.ElevatorState#hasElevatorUserForCurrentFloor()
   */
  @Override
  public boolean hasElevatorUserForCurrentFloor() {
    return elevatorUsers.hasUserFor(currentFloor);
  }

  @Override
  public boolean hasUsersInCurrentDirection() {
    return hasUsersInDirection(currentDirection);
  }

  /* (non-Javadoc)
   * @see org.nca.elevator.ElevatorState#hasUsersInOppositeDirection()
   */
  @Override
  public boolean hasUsersInOppositeDirection() {
    return hasUsersInDirection(currentDirection.flip());
  }

  private boolean hasUsersInDirection(Direction direction) {
    return elevatorUsers.hasUserToward(direction, currentFloor)
        || waitingUsers.hasUserToward(direction, currentFloor);
  }

  @Override
  public int nbUsersInCurrentDirection() {
    return nbUsersInDirection(currentDirection);
  }

  @Override
  public int nbUsersInOppositeDirection() {
    return nbUsersInDirection(currentDirection.flip());
  }

  @Override
  public int scoreInCurrentDirection() {
    return scoreInDirection(currentDirection);
  }

  @Override
  public int scoreInOppositeDirection() {
    return scoreInDirection(currentDirection.flip());
  }

  private int nbUsersInDirection(Direction direction) {
    return elevatorUsers.nbUsersToward(direction, currentFloor)
        + waitingUsers.nbUsersToward(direction, currentFloor);
  }

  private int scoreInDirection(Direction direction) {
    return elevatorUsers.scoreToward(direction, currentFloor)
        + waitingUsers.scoreToward(direction, currentFloor);
  }

  /* (non-Javadoc)
   * @see org.nca.elevator.ElevatorState#isStale()
   */
  @Override
  public boolean isStale() {
    return commandHistory.isStaleSince(3);
  }

  /* (non-Javadoc)
   * @see org.nca.elevator.ElevatorController#doNothing()
   */
  @Override
  public Command doNothing() {
    return Command.NOTHING;
  }

  /* (non-Javadoc)
   * @see org.nca.elevator.ElevatorController#open()
   */
  @Override
  public Command openDoor() {
    elevatorUsers.floorServiced(currentFloor);
    doorState = Door.OPEN;
    return Command.OPEN;
  }

  /* (non-Javadoc)
   * @see org.nca.elevator.ElevatorController#close()
   */
  @Override
  public Command closeDoor() {
    doorState = Door.CLOSED;
    return Command.CLOSE;

  }

  /* (non-Javadoc)
   * @see org.nca.elevator.ElevatorController#goCurrentDirection()
   */
  @Override
  public Command goCurrentDirection() {
    currentFloor = currentFloor + (currentDirection == Direction.UP ? 1 : -1);
    return currentDirection.toCommand();
  }

  /* (non-Javadoc)
   * @see org.nca.elevator.ElevatorController#goOppositeDirection()
   */
  @Override
  public Command goOppositeDirection() {
    currentDirection = currentDirection.flip();
    currentFloor = currentFloor + (currentDirection == Direction.UP ? 1 : -1);
    return currentDirection.toCommand();
  }

  @Override
  public String getStateAsString() {
    return toString();
  }

  @Override
  public String toString() {
    return "Floor: " + currentFloor + ", Dir: " + currentDirection + ", door: " + doorState
        + ", " + waitingUsers + ", " + elevatorUsers;
  }

}
