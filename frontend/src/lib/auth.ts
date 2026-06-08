/**
 * auth.ts — Backwards-compat re-export shim.
 *
 * The cookie-based auth helpers were replaced by `lib/auth-context.tsx`.
 * Components should now use `useAuth()` from `@/lib/auth-context`.
 *
 * This shim preserves the AuthUser type for older imports.
 */
export type { AuthUser } from "./auth-context";
