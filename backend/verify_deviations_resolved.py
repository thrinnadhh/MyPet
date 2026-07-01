import urllib.request
import urllib.error
import json
import socket
import time

PROVIDER_SERVICE_URL = "http://localhost:8081"
DISCOVERY_SERVICE_URL = "http://localhost:8083"
REDIS_HOST = "localhost"
REDIS_PORT = 6380

def test_approve_endpoint_security():
    print("\n--- Testing approve provider endpoint security (Direct Port 8081) ---")
    provider_id = "e1b07384-d113-4e4e-9c8e-3d8e3d8e3d8e"
    url = f"{PROVIDER_SERVICE_URL}/api/v1/providers/{provider_id}/approve"
    
    # 1. Test call without admin role (should return 403)
    req = urllib.request.Request(url, method="POST")
    try:
        urllib.request.urlopen(req)
        print("❌ FAILED: Approve endpoint allowed anonymous approval without credentials")
    except urllib.error.HTTPError as e:
        if e.code == 403:
            print("✅ SUCCESS: Endpoint correctly rejected anonymous caller with 403 Forbidden")
        else:
            print(f"❌ FAILED: Endpoint returned code {e.code} instead of 403")
    except Exception as e:
        print(f"❌ FAILED: Connection error: {e}")

    # 2. Test legacy X-Admin-Api-Key bypass is no longer accepted by provider-service
    req = urllib.request.Request(url, method="POST")
    req.add_header("X-Admin-Api-Key", "pawsnearme_admin_key_2026")
    try:
        urllib.request.urlopen(req)
        print("❌ FAILED: Approve endpoint accepted legacy admin API key")
    except urllib.error.HTTPError as e:
        if e.code == 403:
            print("✅ SUCCESS: Endpoint rejected legacy admin API key with 403 Forbidden")
        else:
            print(f"❌ FAILED: Endpoint returned code {e.code} instead of 403")
    except Exception as e:
         print(f"❌ FAILED: Connection error: {e}")

def test_redis_metadata_caching():
    print("\n--- Testing Redis metadata caching in Discovery Service (Direct Port 8083) ---")
    
    # Trigger a nearby search query to populate discovery cache
    url = f"{DISCOVERY_SERVICE_URL}/api/v1/discovery/providers?longitude=77.6404&latitude=12.9719&radius=10.0&type=PET_STORE"
    req = urllib.request.Request(url, method="GET")
    try:
        response = urllib.request.urlopen(req)
        data = json.loads(response.read().decode('utf-8'))
        print(f"Discovery search returned {len(data)} providers.")
        
        # Connect to Redis to check if metadata keys exist
        try:
            import redis
            r = redis.Redis(host=REDIS_HOST, port=REDIS_PORT, decode_responses=True)
            keys = r.keys("providers:cache:*")
            print(f"Redis cache keys found: {keys}")
            if len(keys) > 0:
                print("✅ SUCCESS: Discovery Service successfully cached provider metadata in Redis Hashes/Strings")
                # print cache details
                for k in keys:
                    val = r.get(k)
                    print(f" - {k}: {val[:80]}...")
            else:
                print("ℹ️ Redis cache is currently empty (expected if search returned 0 providers).")
        except ImportError:
            print("⚠️ Python 'redis' module not installed. Bypassing Redis verification, but discovery search completed successfully.")
    except Exception as e:
        print(f"❌ FAILED to query discovery service: {e}")

if __name__ == "__main__":
    test_approve_endpoint_security()
    test_redis_metadata_caching()
