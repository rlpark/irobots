package rlpark.plugin.irobotview.views;

import static rlpark.plugin.irobot.data.IRobotLabels.BatteryCapacity;
import static rlpark.plugin.irobot.data.IRobotLabels.BatteryCharge;
import static rlpark.plugin.irobot.data.IRobotLabels.BatteryCurrent;
import static rlpark.plugin.irobot.data.IRobotLabels.BatteryTemperature;
import static rlpark.plugin.irobot.data.IRobotLabels.BatteryVoltage;
import static rlpark.plugin.irobot.data.IRobotLabels.Bump;
import static rlpark.plugin.irobot.data.IRobotLabels.Button;
import static rlpark.plugin.irobot.data.IRobotLabels.ChargingState;
import static rlpark.plugin.irobot.data.IRobotLabels.CliffSensor;
import static rlpark.plugin.irobot.data.IRobotLabels.CliffSignal;
import static rlpark.plugin.irobot.data.IRobotLabels.ConnectedHomeBase;
import static rlpark.plugin.irobot.data.IRobotLabels.ConnectedInternalCharger;
import static rlpark.plugin.irobot.data.IRobotLabels.DirtDetect;
import static rlpark.plugin.irobot.data.IRobotLabels.DriveAngle;
import static rlpark.plugin.irobot.data.IRobotLabels.DriveDistance;
import static rlpark.plugin.irobot.data.IRobotLabels.DriveRequested;
import static rlpark.plugin.irobot.data.IRobotLabels.ICLeft;
import static rlpark.plugin.irobot.data.IRobotLabels.ICOmni;
import static rlpark.plugin.irobot.data.IRobotLabels.ICRight;
import static rlpark.plugin.irobot.data.IRobotLabels.LightBumpSensor;
import static rlpark.plugin.irobot.data.IRobotLabels.LightBumpSignal;
import static rlpark.plugin.irobot.data.IRobotLabels.MotorCurrentMainBrush;
import static rlpark.plugin.irobot.data.IRobotLabels.MotorCurrentSideBrush;
import static rlpark.plugin.irobot.data.IRobotLabels.NumberStreamPackets;
import static rlpark.plugin.irobot.data.IRobotLabels.OIMode;
import static rlpark.plugin.irobot.data.IRobotLabels.SongNumber;
import static rlpark.plugin.irobot.data.IRobotLabels.SongPlaying;
import static rlpark.plugin.irobot.data.IRobotLabels.Stasis;
import static rlpark.plugin.irobot.data.IRobotLabels.WallSensor;
import static rlpark.plugin.irobot.data.IRobotLabels.WallSignal;
import static rlpark.plugin.irobot.data.IRobotLabels.WallVirtual;
import static rlpark.plugin.irobot.data.IRobotLabels.WheelDrop;
import static rlpark.plugin.irobot.data.IRobotLabels.WheelEncoder;
import static rlpark.plugin.irobot.data.IRobotLabels.WheelMotorCurrent;
import static rlpark.plugin.irobot.data.IRobotLabels.WheelRequested;
import rlpark.plugin.irobot.data.IRobotLabels;
import rlpark.plugin.rltoys.envio.observations.Legend;
import rlpark.plugin.robot.interfaces.RobotLive;
import zephyr.plugin.core.api.internal.codeparser.codetree.ClassNode;
import zephyr.plugin.core.api.internal.codeparser.interfaces.CodeNode;
import zephyr.plugin.core.api.synchronization.Chrono;
import zephyr.plugin.core.api.synchronization.Clock;
import zephyr.plugin.core.internal.observations.ObsLayout;
import zephyr.plugin.core.internal.observations.ObsWidget;
import zephyr.plugin.core.internal.observations.SensorCollection;
import zephyr.plugin.core.internal.observations.SensorTextGroup;
import zephyr.plugin.core.internal.observations.SensorTextGroup.TextClient;

@SuppressWarnings("restriction")
public class RoombaView extends IRobotView {
  static public class Provider extends IRobotViewProvider {
    static public final Provider instance = new Provider();

    @Override
    public boolean canViewDraw(CodeNode codeNode) {
      if (!super.canViewDraw(codeNode))
        return false;
      return canViewDrawInstance(((ClassNode) codeNode).instance());
    }
  }

  static boolean canViewDrawInstance(Object instance) {
    if (!RobotLive.class.isInstance(instance))
      return false;
    RobotLive problem = (RobotLive) instance;
    return problem.legend().hasLabel(IRobotLabels.LightBumpSensorCenterLeft);
  }

  @Override
  protected ObsLayout getObservationLayout(Clock clock, RobotLive robot) {
    Legend legend = robot.legend();
    SensorTextGroup infoGroup = createInfoGroup(clock, robot.legend());
    SensorCollection wallCollection = new SensorCollection("Walls", createSensorGroup(legend, "Virtual", WallVirtual),
                                                           createSensorGroup(legend, "Sensor", WallSensor),
                                                           createSensorGroup(legend, "Signal", WallSignal));
    SensorCollection odoCollection = new SensorCollection("Odometry", createSensorGroup(legend, "Distance",
                                                                                        DriveDistance),
                                                          createSensorGroup(legend, "Angle", DriveAngle),
                                                          createSensorGroup(legend, "Requested", DriveRequested));
    SensorCollection icCollection = new SensorCollection("Infrared Character",
                                                         createSensorGroup(legend, "Omni", ICOmni),
                                                         createSensorGroup(legend, "Left", ICLeft),
                                                         createSensorGroup(legend, "Right", ICRight));
    SensorCollection powerCollection = new SensorCollection(
                                                            "Battery",
                                                            createSensorGroup(legend, "Current", BatteryCurrent),
                                                            createSensorGroup(legend, "Temperature", BatteryTemperature),
                                                            createSensorGroup(legend, "Charge", BatteryCharge),
                                                            createSensorGroup(legend, "Capacity", BatteryCapacity));
    SensorCollection cliffCollection = new SensorCollection("Cliffs",
                                                            createSensorGroup(legend, "Sensors", CliffSensor),
                                                            createSensorGroup(legend, "Signal", CliffSignal));
    SensorCollection wheelCollection = new SensorCollection("Wheels", createSensorGroup(legend, "Dropped", WheelDrop),
                                                            createSensorGroup(legend, "Requested", WheelRequested),
                                                            createSensorGroup(legend, "Encoder", WheelEncoder),
                                                            createSensorGroup(legend, "Current", WheelMotorCurrent));
    SensorCollection lightBumperCollection = new SensorCollection("Light Bumper", createSensorGroup(legend, "Sensor",
                                                                                                    LightBumpSensor),
                                                                  createSensorGroup(legend, "Signal", LightBumpSignal));
    SensorCollection motorCurrentCollection = new SensorCollection("Brushes", createSensorGroup(legend, "Main",
                                                                                                MotorCurrentMainBrush),
                                                                   createSensorGroup(legend, "Side",
                                                                                     MotorCurrentSideBrush));
    return new ObsLayout(new ObsWidget[][] {
        { infoGroup, createSensorGroup(legend, "Bumper", Bump), wheelCollection, odoCollection,
            createSensorGroup(legend, "Dirt", DirtDetect) },
        { icCollection, cliffCollection, createSensorGroup(legend, "Buttons", Button), motorCurrentCollection,
            createSensorGroup(legend, "Statis", Stasis) }, { wallCollection, lightBumperCollection, powerCollection } });
  }

  private SensorTextGroup createInfoGroup(final Clock clock, Legend legend) {
    TextClient loopTimeTextClient = new TextClient("Loop Time:") {
      @Override
      public String currentText() {
        return Chrono.toPeriodString(clock.lastPeriodNano());
      }
    };
    return new SensorTextGroup("Info", loopTimeTextClient, new IntegerTextClient(legend, ChargingState,
                                                                                 "Charging State:"),
                               new IntegerTextClient(legend, BatteryVoltage, "Voltage:", "00000", "mV"),
                               new IntegerTextClient(legend, ConnectedHomeBase, "Home base: "),
                               new IntegerTextClient(legend, ConnectedInternalCharger, "Internal charger: "),
                               new IntegerTextClient(legend, OIMode, "OI Mode: "), new IntegerTextClient(legend,
                                                                                                         SongNumber,
                                                                                                         "Song: "),
                               new IntegerTextClient(legend, SongPlaying, "Playing: "),
                               new IntegerTextClient(legend, NumberStreamPackets, "Packets: "));
  }

  @Override
  protected boolean isInstanceSupported(Object instance) {
    return canViewDrawInstance(instance);
  }
}
