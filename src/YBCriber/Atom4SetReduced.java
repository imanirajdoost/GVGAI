package YBCriber;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import tools.ElapsedCpuTimer;

public class Atom4SetReduced extends AtomSet{
  private Set<ArrayList<Long>> atoms;

  public Atom4SetReduced(BfsNode node, ElapsedCpuTimer timer, int TIME_MARGIN){
    final int MAX_ATOMS_SIZE = Agent.h * Agent.w * 4;
    if (node == null){
      if (Agent.deterministic) this.atoms = new HashSet<>(MAX_ATOMS_SIZE * 2 * 700);
      else this.atoms = new HashSet<>();
      return;
    }
    this.atoms = new HashSet<>();
    if (node == null) return;
    boolean counting = (timer != null);

    // avatar atom
    long aAtom = getAvatarAtom(node) * NUM_ATOM_TYPES + 0;

    // observation atoms (missing obs and obs type together)
    ArrayList<Long> obsAtoms = new ArrayList<>();
    ArrayList<Long> rawMissingObsIdAtoms = getMissingObsIdAtoms(node);
    for (long rmoiAtom : rawMissingObsIdAtoms) {
      obsAtoms.add(rmoiAtom * NUM_ATOM_TYPES + 1);
    }
    ArrayList<Long> rawObsTypeAtoms = getObsTypeAtoms(node);
    for (long rotAtom : rawObsTypeAtoms) {
      obsAtoms.add(rotAtom * NUM_ATOM_TYPES + 2);
    }

    // resource atoms
    ArrayList<Long> resourceAtoms = new ArrayList<>();
    ArrayList<Long> rawResourceAtoms = getResourceAtoms(node);
    for (long rrAtom : rawResourceAtoms) {
      resourceAtoms.add(rrAtom * NUM_ATOM_TYPES + 3);
    }

    // score atom
    long sAtom = getScoreAtom(node) * NUM_ATOM_TYPES + 4;

    for (int i = 0; i < obsAtoms.size(); ++i) {
      long oAtom = obsAtoms.get(i);
      if (counting) if (timer.remainingTimeMillis() < TIME_MARGIN) return;
      for (int i2 = i + 1; i2 < obsAtoms.size(); ++i2) {
        long oAtom2 = obsAtoms.get(i2);
        if (counting) if (timer.remainingTimeMillis() < TIME_MARGIN) return;
        // {avatar, object, object, object}
        for (int i3 = i2 + 1; i3 < obsAtoms.size(); ++i3) {
          long oAtom3 = obsAtoms.get(i3);
          this.atoms.add(getList(aAtom, oAtom, oAtom2, oAtom3));
          if (this.atoms.size() == MAX_ATOMS_SIZE) return;
        }

        // {avatar, score, object, object}
        this.atoms.add(getList(aAtom, sAtom, oAtom, oAtom2));
        if (this.atoms.size() == MAX_ATOMS_SIZE) return;

        if (counting) if (timer.remainingTimeMillis() < TIME_MARGIN) return;

        // {avatar, resource, object, object}
        for (long rAtom : resourceAtoms){
          this.atoms.add(getList(aAtom, rAtom, oAtom, oAtom2));
          if (this.atoms.size() == MAX_ATOMS_SIZE) return;
        }
      }

      // {avatar, score, resource, observation}
      for (long rAtom : resourceAtoms){
        this.atoms.add(getList(aAtom, sAtom, rAtom, oAtom));
        if (this.atoms.size() == MAX_ATOMS_SIZE) return;
      }
    }

    // {avatar, object, object, object}
    // {avatar, score, object, object} -
    // {avatar, resource, object, object} -
    // {avatar, score, resource, object} -
    //TODO: Use these 4? A more efficient way?
  }

  private ArrayList<Long> getList(long i, long j, long k, long l) {
    ArrayList<Long> aux = new ArrayList<>();
    aux.add(i);
    aux.add(j);
    aux.add(k);
    aux.add(l);
    Collections.sort(aux);
    return aux;
  }

  @Override
  boolean addAll(AtomSet as) {
    if (!(as instanceof Atom4SetReduced)) {
      return super.containsAll(as);
    }
    Atom4SetReduced a4sr = (Atom4SetReduced)as;
    return this.atoms.addAll(a4sr.atoms);
  }

  @Override
  boolean containsAll(AtomSet as) {
    if (!(as instanceof Atom4SetReduced)) {
      return super.containsAll(as);
    }
    Atom4SetReduced a4sr = (Atom4SetReduced)as;
    return this.atoms.containsAll(a4sr.atoms);
  }

  @Override
  int size() {
    return this.atoms.size();
  }
}

