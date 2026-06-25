package com.platform.agent.api;

import com.platform.agent.hub.ReviewGateway;
import com.platform.common.agent.ReviewDecision;
import com.platform.core.repository.AgentReviewRequestRepository;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/hub/reviews")
public class ReviewDecisionController {

  private final ReviewGateway reviewGateway;
  private final AgentReviewRequestRepository reviewRepo;

  public ReviewDecisionController(
      ReviewGateway reviewGateway, AgentReviewRequestRepository reviewRepo) {
    this.reviewGateway = reviewGateway;
    this.reviewRepo = reviewRepo;
  }

  @PostMapping("/{reviewRequestId}/decide")
  public ResponseEntity<Void> decide(
      @PathVariable UUID reviewRequestId, @RequestBody DecisionRequest request) {
    ReviewDecision decision = ReviewDecision.valueOf(request.decision().toUpperCase());
    reviewGateway.applyDecision(reviewRequestId, decision, request.editedPayload());
    return ResponseEntity.ok().build();
  }

  @GetMapping("/{reviewRequestId}")
  public ResponseEntity<?> get(@PathVariable UUID reviewRequestId) {
    return reviewRepo
        .findById(reviewRequestId)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping
  public ResponseEntity<?> listPending(@RequestParam UUID workflowId) {
    return ResponseEntity.ok(reviewRepo.findByWorkflowIdAndStatus(workflowId, "PENDING"));
  }

  public record DecisionRequest(String decision, String editedPayload) {}
}
