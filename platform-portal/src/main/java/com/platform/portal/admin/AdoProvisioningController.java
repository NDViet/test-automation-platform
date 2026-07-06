package com.platform.portal.admin;

import com.platform.portal.admin.AdoProvisioningService.ProvisionSummary;
import com.platform.security.web.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin API to provision a project's ADO team members as platform users (super-admin / org-admin
 * only — enforced in {@link AdoProvisioningService}). The acting principal comes from the verified
 * JWT via {@link CurrentUser}, never a request body.
 */
@RestController
@RequestMapping("/api/portal/admin/projects/{projectId}")
@Tag(name = "Admin — ADO provisioning")
public class AdoProvisioningController {

  private final AdoProvisioningService service;

  public AdoProvisioningController(AdoProvisioningService service) {
    this.service = service;
  }

  @PostMapping("/provision-ado-users")
  @Operation(summary = "Provision the project's ADO team members as assignable platform users")
  public ProvisionSummary provision(@PathVariable UUID projectId) {
    return service.provisionProject(projectId, CurrentUser.get());
  }
}
