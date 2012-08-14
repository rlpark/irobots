package rlpark.example.irobot.examples;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import rlpark.plugin.irobot.data.CreateAction;
import rlpark.plugin.irobot.robots.CreateRobot;
import rlpark.plugin.irobot.robots.IRobotEnvironment;
import rlpark.plugin.rltoys.algorithms.LinearLearner;
import rlpark.plugin.rltoys.algorithms.discovery.ltu.RepresentationDiscovery;
import rlpark.plugin.rltoys.algorithms.discovery.sorting.WeightSorter;
import rlpark.plugin.rltoys.algorithms.functions.policydistributions.helpers.RandomPolicy;
import rlpark.plugin.rltoys.algorithms.predictions.td.TDLambda;
import rlpark.plugin.rltoys.algorithms.representations.ltu.StateUpdate;
import rlpark.plugin.rltoys.algorithms.representations.ltu.networks.AutoRegulatedNetwork;
import rlpark.plugin.rltoys.algorithms.representations.ltu.networks.RandomNetwork;
import rlpark.plugin.rltoys.algorithms.representations.ltu.units.LTU;
import rlpark.plugin.rltoys.algorithms.representations.ltu.units.LTUAdaptive;
import rlpark.plugin.rltoys.algorithms.traces.RTraces;
import rlpark.plugin.rltoys.envio.actions.Action;
import rlpark.plugin.rltoys.envio.observations.Legend;
import rlpark.plugin.rltoys.envio.policy.Policies;
import rlpark.plugin.rltoys.horde.Horde;
import rlpark.plugin.rltoys.horde.demons.PredictionDemon;
import rlpark.plugin.rltoys.horde.demons.PredictionDemonVerifier;
import rlpark.plugin.rltoys.horde.functions.RewardFunction;
import rlpark.plugin.rltoys.horde.functions.RewardObservationFunction;
import rlpark.plugin.rltoys.math.GrayCode;
import rlpark.plugin.rltoys.math.vector.implementations.BVector;
import rlpark.plugin.rltoys.utils.Utils;
import rlpark.plugin.robot.observations.ObservationVersatile;
import zephyr.plugin.core.api.Zephyr;
import zephyr.plugin.core.api.labels.Labels;
import zephyr.plugin.core.api.monitoring.annotations.LabelProvider;
import zephyr.plugin.core.api.monitoring.annotations.Monitor;
import zephyr.plugin.core.api.synchronization.Clock;

@SuppressWarnings("restriction")
@Monitor
public class CreateRawDataRecursiveRandomNetworkNexting implements Runnable {
  public final static double[] Gammas = new double[] { .9, .99 };
  protected static final double MinDensity = 0.01;
  protected static final double MaxDensity = 0.05;
  private final int NetworkOutputVectorSize = 10000;
  private final Clock clock = new Clock("Nexting");
  private final IRobotEnvironment environment = new CreateRobot();
  private final Random random = new Random(0);
  private final RandomPolicy policy = new RandomPolicy(random, CreateAction.AllActions);
  private final int rawObsVectorSize = environment.observationPacketSize() * 8;
  private final LTU prototype = new LTUAdaptive(MinDensity, MaxDensity, 0.99, .001);
  private final Horde horde;
  private final PredictionDemonVerifier[] verifiers;
  private final StateUpdate stateUpdate;
  private final RepresentationDiscovery discovery;
  private BVector x_t;
  private Action a_t;
  double error;

  public CreateRawDataRecursiveRandomNetworkNexting() {
    RandomNetwork representation = new AutoRegulatedNetwork(random, NetworkOutputVectorSize + rawObsVectorSize + 1,
                                                            NetworkOutputVectorSize, MinDensity, MaxDensity);
    stateUpdate = new StateUpdate(representation, rawObsVectorSize);
    List<RewardObservationFunction> rewardFunctions = createRewardFunctions(environment.legend());
    int stateVectorSize = stateUpdate.stateSize();
    List<PredictionDemon> demons = createNextingDemons(rewardFunctions, Gammas, MaxDensity * stateVectorSize,
                                                       stateVectorSize);
    horde = new Horde(demons, rewardFunctions);
    verifiers = createDemonVerifiers(demons);
    WeightSorter sorter = new WeightSorter(extractLinearLearners(demons), 0, representation.outputSize);
    discovery = new RepresentationDiscovery(random, representation, sorter, prototype, NetworkOutputVectorSize / 10, 5);
    discovery.fillNetwork();
    Zephyr.advertise(clock, this);
  }

  private LinearLearner[] extractLinearLearners(List<PredictionDemon> demons) {
    LinearLearner[] learners = new LinearLearner[demons.size()];
    for (int i = 0; i < learners.length; i++)
      learners[i] = demons.get(i).learner();
    return learners;
  }

  private PredictionDemonVerifier[] createDemonVerifiers(List<PredictionDemon> demons) {
    PredictionDemonVerifier[] verifiers = new PredictionDemonVerifier[demons.size()];
    for (int i = 0; i < verifiers.length; i++) {
      verifiers[i] = new PredictionDemonVerifier(demons.get(i));
    }
    return verifiers;
  }

  @LabelProvider(ids = { "verifiers" })
  String labelOf(int index) {
    return Labels.label(horde.demonLabel(index));
  }

  private List<RewardObservationFunction> createRewardFunctions(Legend legend) {
    List<String> labels = legend.getLabels();
    RewardObservationFunction[] rewardFunctions = new RewardObservationFunction[labels.size()];
    for (int i = 0; i < rewardFunctions.length; i++)
      rewardFunctions[i] = new RewardObservationFunction(legend, labels.get(i));
    return Utils.asList(rewardFunctions);
  }

  private List<PredictionDemon> createNextingDemons(List<RewardObservationFunction> rewardFunctions, double[] gammas,
      double stateFeatureNorm, int vectorSize) {
    List<PredictionDemon> demons = new ArrayList<PredictionDemon>();
    for (RewardFunction rewardFunction : rewardFunctions) {
      for (double gamma : gammas) {
        double alpha = .1 / stateFeatureNorm;
        int nbFeatures = vectorSize;
        TDLambda td = new TDLambda(.7, gamma, alpha, nbFeatures, new RTraces());
        demons.add(new PredictionDemon(rewardFunction, td));
      }
    }
    return demons;
  }

  @Override
  public void run() {
    while (!environment.isClosed() && clock.tick()) {
      ObservationVersatile lastObs = environment.waitNewRawObs().last();
      BVector binaryObs = BVector.toBinary(GrayCode.toGrayCode(lastObs.rawData()));
      BVector x_tp1 = stateUpdate.updateState(binaryObs);
      horde.update(lastObs, x_t, a_t, x_tp1);
      for (PredictionDemonVerifier verifier : verifiers)
        error = verifier.update(false);
      if (clock.timeStep() % 1000 == 0)
        discovery.changeRepresentation(1);
      a_t = Policies.decide(policy, x_tp1);
      environment.sendAction((CreateAction) a_t);
      x_t = x_tp1;
    }
  }

  public static void main(String[] args) {
    new CreateRawDataRecursiveRandomNetworkNexting().run();
  }
}
