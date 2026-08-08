import { fetchRecurringOccurrences } from '../recurring-orders';

jest.mock('@/utils/app-config', () => ({
  appConfig: {
    apiBaseUrl: 'https://api.mypet.test',
    allowDemoMode: false,
  },
}));

const mockedFetch = jest.fn();

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: jest.fn().mockResolvedValue(body),
  } as unknown as Response;
}

describe('recurring order occurrence history', () => {
  beforeEach(() => {
    mockedFetch.mockReset();
    global.fetch = mockedFetch as unknown as typeof fetch;
  });

  it('loads authenticated occurrence history from the owning subscription', async () => {
    mockedFetch.mockResolvedValueOnce(jsonResponse([
      {
        occurrenceId: 'occurrence-1',
        subscriptionId: 'subscription-1',
        scheduledFor: '2026-08-15T00:00:00Z',
        orderId: 'order-1',
        status: 'ORDER_CREATED',
        failureCode: null,
        failureDetail: null,
        createdAt: '2026-08-15T00:00:01Z',
        updatedAt: '2026-08-15T00:00:02Z',
      },
    ]));

    await expect(fetchRecurringOccurrences('subscription-1', 'access-token')).resolves.toEqual([
      expect.objectContaining({
        occurrenceId: 'occurrence-1',
        orderId: 'order-1',
        status: 'ORDER_CREATED',
      }),
    ]);

    expect(mockedFetch).toHaveBeenCalledWith(
      'https://api.mypet.test/api/v1/orders/subscriptions/subscription-1/occurrences',
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: 'Bearer access-token',
        }),
      }),
    );
  });
});
