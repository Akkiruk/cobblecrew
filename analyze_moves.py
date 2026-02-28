"""
Analyze all Pokémon moves from Cobblemon species data.
Outputs: move name, # species that can learn it (excluding legacy), learn methods.
Also shows which CobbleCrew jobs currently use each move.
"""

import zipfile
import json
import re
import os
from collections import defaultdict

JAR = os.path.join(os.path.dirname(__file__), "..", "mods", "Cobblemon-fabric-1.7.1+1.21.1.jar")
JOBS_DIR = os.path.join(os.path.dirname(__file__), "common", "src", "main", "kotlin", "akkiruk", "cobblecrew", "jobs", "registry")

def extract_job_moves():
    """Parse all job registry .kt files to find which moves each job uses."""
    job_moves = {}  # move -> list of job names
    all_jobs = {}   # job name -> set of moves

    for filename in os.listdir(JOBS_DIR):
        if not filename.endswith(".kt"):
            continue
        path = os.path.join(JOBS_DIR, filename)
        with open(path, "r", encoding="utf-8") as f:
            content = f.read()

        # Find job definitions: name = "xxx" followed by qualifyingMoves = setOf(...)
        # Parse blocks between each val/object declaration
        current_name = None
        for line in content.split("\n"):
            name_match = re.search(r'name\s*=\s*"([^"]+)"', line)
            if name_match:
                current_name = name_match.group(1)

            moves_match = re.search(r'qualifyingMoves\s*=\s*setOf\(([^)]+)\)', line)
            if moves_match and current_name:
                moves_str = moves_match.group(1)
                moves = [m.strip().strip('"') for m in moves_str.split(",")]
                all_jobs[current_name] = set(moves)
                for move in moves:
                    if move not in job_moves:
                        job_moves[move] = []
                    job_moves[move].append(current_name)
                current_name = None

    return job_moves, all_jobs


def analyze_moves():
    job_moves, all_jobs = extract_job_moves()

    zf = zipfile.ZipFile(JAR)
    species_files = [n for n in zf.namelist()
                     if n.startswith("data/cobblemon/species/") and n.endswith(".json")]

    move_species = defaultdict(set)       # move -> set of species names
    move_methods = defaultdict(set)       # move -> set of learn methods
    species_count = 0
    implemented_count = 0

    for sf in species_files:
        try:
            data = json.loads(zf.read(sf))
        except:
            continue

        name = data.get("name", os.path.basename(sf).replace(".json", ""))
        implemented = data.get("implemented", False)
        if not implemented:
            continue
        implemented_count += 1
        species_count += 1

        moves_list = data.get("moves", [])
        for entry in moves_list:
            if ":" not in entry:
                continue
            method, move = entry.split(":", 1)
            move = move.strip().lower()
            method = method.strip().lower()

            # Skip legacy moves — not actually learnable in current gen
            if method == "legacy":
                continue

            move_species[move].add(name)
            move_methods[move].add(method)

    zf.close()

    # Sort by number of species (descending)
    sorted_moves = sorted(move_species.items(), key=lambda x: -len(x[1]))

    # Print header
    print(f"Total implemented species: {implemented_count}")
    print(f"Total learnable moves (excluding legacy): {len(sorted_moves)}")
    print(f"Total CobbleCrew jobs: {len(all_jobs)}")
    print()

    # Print moves used by jobs that are shared between multiple jobs
    conflicts = {m: jobs for m, jobs in job_moves.items() if len(jobs) > 1}
    if conflicts:
        print("=== MOVE CONFLICTS (used by multiple jobs) ===")
        for move, jobs in sorted(conflicts.items()):
            count = len(move_species.get(move, set()))
            print(f"  {move}: {', '.join(jobs)}  ({count} species)")
        print()

    # Section 1: Moves currently used by jobs (with species count)
    print("=" * 60)
    print("SECTION 1: MOVES USED BY COBBLECREW JOBS")
    print("=" * 60)
    used_moves = set()
    for jobs_list in job_moves.values():
        pass  # just building set below
    for move, jobs in sorted(job_moves.items(), key=lambda x: x[0]):
        count = len(move_species.get(move, set()))
        used_moves.add(move)
        print(f"  {move} ({count} species) -> {', '.join(jobs)}")

    # Section 2: ALL moves sorted by species count
    print()
    print("=" * 60)
    print("SECTION 2: ALL MOVES BY SPECIES COUNT")
    print("(* = used by a CobbleCrew job)")
    print("=" * 60)
    for move, species_set in sorted_moves:
        marker = " *" if move in used_moves else ""
        print(f"  {move}: {len(species_set)}{marker}")


if __name__ == "__main__":
    analyze_moves()
