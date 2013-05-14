package rlpark.plugin.irobot.internal.descriptors;

import rlpark.plugin.robot.internal.statemachine.StateNode;

public interface SerialLinkNode extends StateNode<Byte> {
  int sum();
}
