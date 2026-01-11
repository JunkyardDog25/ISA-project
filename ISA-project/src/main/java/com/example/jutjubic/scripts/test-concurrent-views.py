#!/usr/bin/env python3
"""
Skripta za testiranje thread-safe view count increment.
Simulira više korisnika koji istovremeno pristupaju videu.
"""

import requests
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed

# Konfiguracija
BASE_URL = "http://localhost:8080"
VIDEO_ID = "0c530c10-5a34-4e66-941c-98ec1cdeb652"
NUM_CONCURRENT_REQUESTS = 50

def increment_view():
    """Funkcija koja šalje zahtev za inkrementaciju pregleda."""
    try:
        response = requests.put(f"{BASE_URL}/api/videos/{VIDEO_ID}/views", timeout=10)
        if response.status_code == 200:
            return {"success": True, "view_count": response.json().get("views")}
        else:
            return {"success": False, "error": f"Status {response.status_code}"}
    except Exception as e:
        return {"success": False, "error": str(e)}

def get_video():
    """Funkcija koja dobija trenutni view count videa."""
    try:
        response = requests.get(f"{BASE_URL}/api/videos/{VIDEO_ID}", timeout=10)
        if response.status_code == 200:
            return response.json().get("viewCount", 0)
        else:
            return None
    except Exception as e:
        print(f"Greška pri dobijanju videa: {e}")
        return None

def main():
    print("=" * 50)
    print("Thread-Safe View Count Test")
    print("=" * 50)
    print()
    
    # Korak 1: Proveri da li video postoji
    print(f"Korak 1: Provera video ID: {VIDEO_ID}")
    before_count = get_video()
    
    if before_count is None:
        print(f"✗ Greška: Video sa ID {VIDEO_ID} nije pronađen ili server nije dostupan!")
        return
    
    print(f"✓ Video pronađen")
    print(f"  Početni view count: {before_count}")
    print()
    
    # Korak 2: Simuliraj concurrent zahteve
    print(f"Korak 2: Simuliranje {NUM_CONCURRENT_REQUESTS} istovremenih zahteva...")
    print(f"  Ovo simulira {NUM_CONCURRENT_REQUESTS} korisnika koji istovremeno pristupaju videu")
    print()
    
    start_time = time.time()
    successful_requests = 0
    failed_requests = 0
    
    # Koristi ThreadPoolExecutor za concurrent zahteve
    with ThreadPoolExecutor(max_workers=NUM_CONCURRENT_REQUESTS) as executor:
        # Pokreni sve zahteve istovremeno
        futures = [executor.submit(increment_view) for _ in range(NUM_CONCURRENT_REQUESTS)]
        
        # Prikupi rezultate
        for future in as_completed(futures):
            result = future.result()
            if result["success"]:
                successful_requests += 1
            else:
                failed_requests += 1
    
    end_time = time.time()
    duration = (end_time - start_time) * 1000  # u milisekundama
    
    print(f"  ✓ Završeno za {duration:.2f} ms")
    print(f"  ✓ Uspešnih zahteva: {successful_requests}")
    if failed_requests > 0:
        print(f"  ✗ Neuspešnih zahteva: {failed_requests}")
    print()
    
    # Korak 3: Proveri finalni view count
    print("Korak 3: Provera finalnog view count-a...")
    time.sleep(0.5)  # Kratka pauza da se transakcije završe
    after_count = get_video()
    
    if after_count is None:
        print("✗ Greška: Nije moguće dobiti finalni view count!")
        return
    
    print(f"✓ Finalni view count: {after_count}")
    print()
    
    # Korak 4: Proveri konzistentnost
    print("=" * 50)
    print("Rezultati")
    print("=" * 50)
    print(f"Početni view count:  {before_count}")
    print(f"Finalni view count:  {after_count}")
    print(f"Očekivano povećanje: {successful_requests}")
    print(f"Stvarno povećanje:   {after_count - before_count}")
    print()
    
    expected_final = before_count + successful_requests
    
    if after_count == expected_final:
        print("✓ SUCCESS: View count je konzistentan!")
        print(f"  Sva {successful_requests} istovremena pregleda su pravilno zabeležena.")
    else:
        print("✗ FAILURE: View count nije konzistentan!")
        print(f"  Očekivano: {expected_final}")
        print(f"  Stvarno:   {after_count}")
        lost_views = expected_final - after_count
        print(f"  Izgubljeno pregleda: {lost_views}")
    
    print()
    print("=" * 50)

if __name__ == "__main__":
    main()
