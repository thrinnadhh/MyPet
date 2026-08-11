import React from 'react';

import { AppBar, RoleBadge, StateView } from '@/components/foundation/primitives';
import { ScreenShell } from '@/components/foundation/screen-shell';
import { useAuth } from '@/context/AuthContext';

/**
 * Sprint 4 admin-client consolidation.
 *
 * Merchant/Captain operations stay in this mobile application. The ADMIN
 * control plane lives in apps/super-admin-web and consumes the single shared
 * backend Admin API/RBAC contract. This compatibility route intentionally does
 * not duplicate administrative state or mutation logic.
 */
export default function AdminCompatibilityScreen() {
  const { role } = useAuth();

  if (role !== 'ADMIN') {
    return (
      <ScreenShell
        header={<AppBar eyebrow="MY PET OPERATIONS" title="Admin" action={<RoleBadge role="admin" />} />}
      >
        <StateView
          kind="unauthorized"
          title="Administrator access required"
          message="This compatibility route is available only to accounts carrying the canonical ADMIN role."
        />
      </ScreenShell>
    );
  }

  return (
    <ScreenShell
      testID="admin-operations-portal"
      header={
        <AppBar
          eyebrow="MY PET OPERATIONS"
          title="Admin"
          subtitle="Administrative control plane consolidated into the web console"
          action={<RoleBadge role="admin" />}
        />
      }
    >
      <StateView
        kind="empty"
        title="Use the Admin web console"
        message="Merchant and Captain operations remain in this mobile app. Administrative dashboards, approvals, service areas, disputes, support, audit history and lifecycle controls now use the single ADMIN web control plane and the shared backend Admin API."
      />
    </ScreenShell>
  );
}
