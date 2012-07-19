package rlpark.plugin.irobot.examples;

import rlpark.plugin.irobot.data.IRobotSongs;
import rlpark.plugin.irobot.robots.CreateRobot;
import rlpark.plugin.irobot.robots.IRobotEnvironment;

public class CreateMusicClient {
  public static void main(String[] args) {
    IRobotEnvironment robot = new CreateRobot();
    robot.safeMode();
    robot.waitNewObs();
    robot.sendLeds(0, 255, true, true);
    while (true) {
      robot.playSong(IRobotSongs.DarthVador);
      try {
        Thread.sleep(10000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }
}
