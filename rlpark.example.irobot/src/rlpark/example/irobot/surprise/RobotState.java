package rlpark.example.irobot.surprise;

import rlpark.plugin.rltoys.algorithms.functions.states.AgentState;
import rlpark.plugin.rltoys.envio.actions.Action;
import rlpark.plugin.rltoys.envio.observations.Observation;
import rlpark.plugin.rltoys.math.vector.RealVector;
import rlpark.plugin.rltoys.math.vector.implementations.PVector;

public class RobotState implements AgentState {
  private static final long serialVersionUID = 6644415896368916415L;

  @Override
  public RealVector update(Action a_t, Observation o_tp1) {
    return new PVector(new double[] { 1.0 });
  }

  @Override
  public double stateNorm() {
    return 1;
  }

  @Override
  public int stateSize() {
    return 1;
  }
}
