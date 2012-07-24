package rlpark.plugin.irobotview.views;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.action.IToolBarManager;

import rlpark.plugin.irobot.logfiles.IRobotLogFile;
import rlpark.plugin.irobotview.filehandlers.IRobotLogFileHandler;
import rlpark.plugin.rltoys.envio.observations.Legend;
import rlpark.plugin.rltoys.utils.Utils;
import rlpark.plugin.robot.helpers.Robots;
import rlpark.plugin.robot.interfaces.RobotLive;
import zephyr.plugin.core.ZephyrCore;
import zephyr.plugin.core.api.synchronization.Clock;
import zephyr.plugin.core.internal.actions.RestartAction;
import zephyr.plugin.core.internal.actions.TerminateAction;
import zephyr.plugin.core.internal.helpers.ClassViewProvider;
import zephyr.plugin.core.internal.observations.EnvironmentView;
import zephyr.plugin.core.internal.observations.SensorGroup;
import zephyr.plugin.core.internal.observations.SensorTextGroup.TextClient;
import zephyr.plugin.core.internal.views.Restartable;

@SuppressWarnings("restriction")
public abstract class IRobotView extends EnvironmentView<RobotLive> implements Restartable {
  static abstract public class IRobotViewProvider extends ClassViewProvider {
    public IRobotViewProvider() {
      super(RobotLive.class);
    }
  }

  protected class IntegerTextClient extends TextClient {
    private final String defaultString;
    private final int labelIndex;
    private final String suffix;

    public IntegerTextClient(Legend legend, String obsLabel, String textLabel) {
      this(legend, obsLabel, textLabel, "0");
    }

    public IntegerTextClient(Legend legend, String obsLabel, String textLabel, String defaultString) {
      this(legend, obsLabel, textLabel, "0", "");
    }

    public IntegerTextClient(Legend legend, String obsLabel, String textLabel, String defaultString, String suffix) {
      super(textLabel);
      labelIndex = legend.indexOf(obsLabel);
      this.defaultString = defaultString;
      this.suffix = suffix;
      assert labelIndex >= 0;
    }

    @Override
    public String currentText() {
      if (currentObservation == null)
        return defaultString + suffix;
      return String.valueOf((int) currentObservation[labelIndex]) + suffix;
    }
  }

  protected double[] currentObservation;
  private final TerminateAction terminateAction;
  private final RestartAction restartAction;
  String filepath;

  public IRobotView() {
    terminateAction = new TerminateAction(this);
    terminateAction.setEnabled(false);
    restartAction = new RestartAction(this);
    restartAction.setEnabled(false);
  }

  @Override
  protected void setToolbar(IToolBarManager toolBarManager) {
    toolBarManager.add(restartAction);
    toolBarManager.add(terminateAction);
  }

  protected SensorGroup createSensorGroup(Legend legend, String title, String prefix) {
    return new SensorGroup(title, startsWith(legend, prefix));
  }

  private int[] startsWith(Legend legend, String prefix) {
    List<Integer> result = new ArrayList<Integer>();
    for (Map.Entry<String, Integer> entry : legend.legend().entrySet()) {
      String label = entry.getKey();
      if (label.startsWith(prefix))
        result.add(entry.getValue());
    }
    Collections.sort(result);
    return Utils.asIntArray(result);
  }

  @Override
  public boolean synchronize(RobotLive current) {
    currentObservation = Robots.toDoubles(current.lastReceivedRawObs());
    synchronize(currentObservation);
    return true;
  }

  @Override
  protected void setLayout(Clock clock, RobotLive current) {
    super.setLayout(clock, current);
    boolean restartable = current instanceof IRobotLogFile;
    filepath = restartable ? ((IRobotLogFile) current).filepath() : null;
    restartAction.setEnabled(restartable);
    terminateAction.setEnabled(true);
    setViewTitle(current);
  }

  private void setViewTitle(RobotLive robot) {
    if (robot == null) {
      setViewName("Observation", "");
      return;
    }
    IRobotLogFile logFile = robot instanceof IRobotLogFile ? (IRobotLogFile) robot : null;
    String viewTitle = logFile == null ? robot.getClass().getSimpleName() : new File(logFile.filepath()).getName();
    String tooltip = logFile == null ? "" : logFile.filepath();
    setViewName(viewTitle, tooltip);
  }

  @Override
  protected void unsetLayout() {
    super.unsetLayout();
    restartAction.setEnabled(false);
    terminateAction.setEnabled(false);
  }

  @Override
  public void restart() {
    assert filepath != null;
    close();
    ZephyrCore.start(new Runnable() {
      @Override
      public void run() {
        IRobotLogFileHandler.handle(filepath);
      }
    });
  }
}