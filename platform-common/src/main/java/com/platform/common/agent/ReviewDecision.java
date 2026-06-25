package com.platform.common.agent;

/** Human decision on a review request. Drives the next FSM transition. */
public enum ReviewDecision {
  APPROVED, // proceed; node commits or pushes the artifact
  REJECTED, // discard artifact; mark session CANCELLED or log as MANUAL_INVESTIGATION
  EDIT, // return to originating node with revision_prompt; opens a new session
  DEFER // re-queue for later; extends the review request expiry by 24 h
}
