package com.platform.common.agent;

public enum NodeResultStatus {
  COMPLETED, // all tasks done, artifacts raised
  AWAITING_REVIEW, // artifact produced, human decision pending
  AWAITING_INPUT, // agent needs clarification from the user before continuing
  FAILED, // terminal error; retriable flag indicates whether to retry
  PARTIAL // some tasks done, remaining steps in a follow-on session
}
