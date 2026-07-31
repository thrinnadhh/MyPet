// Deployment-time public configuration. Replace these values during deployment.
// The Supabase anon key is public by design; authorization remains enforced by JWT claims and backend checks.
window.PNM_ADMIN_CONFIG = window.PNM_ADMIN_CONFIG || {
    apiBaseUrl: '',
    supabaseUrl: '',
    supabaseAnonKey: '',
};
