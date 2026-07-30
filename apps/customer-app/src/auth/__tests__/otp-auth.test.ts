const mockSignInWithOtp = jest.fn();
const mockVerifyOtp = jest.fn();
const mockResend = jest.fn();
jest.mock('@/utils/supabase', () => ({ supabase: { auth: { signInWithOtp: (...args: unknown[]) => mockSignInWithOtp(...args), verifyOtp: (...args: unknown[]) => mockVerifyOtp(...args), resend: (...args: unknown[]) => mockResend(...args) } } }));

import { normalizeEmail, normalizeOtpError, normalizePhone, resendOtp, sendOtp, verifyOtp } from '@/auth/otp-auth';

describe('OTP authentication', () => {
  beforeEach(() => jest.clearAllMocks());
  it('normalizes Indian mobile and email identifiers', () => {
    expect(normalizePhone('98765 43210')).toBe('+919876543210');
    expect(normalizeEmail(' USER@Example.com ')).toBe('user@example.com');
  });
  it('sends and verifies mobile OTP', async () => {
    mockSignInWithOtp.mockResolvedValue({ error: null });
    mockVerifyOtp.mockResolvedValue({ data: { session: { user: { id: 'user-1' } } }, error: null });
    const phone = await sendOtp('phone', '9876543210');
    await expect(verifyOtp('phone', phone, '123456')).resolves.toBeTruthy();
    expect(mockSignInWithOtp).toHaveBeenCalledWith(expect.objectContaining({ phone: '+919876543210' }));
  });
  it('supports email OTP and resend', async () => {
    mockSignInWithOtp.mockResolvedValue({ error: null });
    const email = await sendOtp('email', 'user@example.com');
    await resendOtp('email', email);
    expect(mockSignInWithOtp).toHaveBeenLastCalledWith(expect.objectContaining({ email }));
  });
  it.each([
    [{ status: 429, message: 'rate limit' }, 'RATE_LIMITED'],
    [{ status: 400, message: 'token expired' }, 'EXPIRED_CODE'],
    [{ status: 400, message: 'invalid token' }, 'INVALID_CODE'],
    [{ message: 'Failed to fetch' }, 'NETWORK'],
  ])('normalizes Supabase failure %o', (error, code) => expect(normalizeOtpError(error).code).toBe(code));
});
