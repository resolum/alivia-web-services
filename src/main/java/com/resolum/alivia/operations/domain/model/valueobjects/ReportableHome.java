package com.resolum.alivia.operations.domain.model.valueobjects;

import java.util.List;

/**
 * The home of the account as the report form shows it: the installed devices (numbered from 1 in the order the
 * technician registered them) and the address the visits go to.
 */
public record ReportableHome(String draftId, String directions, String contactName, List<InstalledDevice> devices) {
}
