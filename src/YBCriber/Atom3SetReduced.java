package YBCriber;

import core.game.StateObservation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import tools.ElapsedCpuTimer;

public class Atom3SetReduced extends AtomSet {
  private Set<ArrayList<Long>> atoms;

  public Atom3SetReduced(BfsNode node, ElapsedCpuTimer timer, int TIME_MARGIN) {
    final int MAX_ATOMS_SIZE = Agent.h * Agent.w * 4;
    if (node == null) {
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
      // {avatar, object, object}
      for (int i2 = i + 1; i2 < obsAtoms.size(); ++i2) {
        long oAtom2 = obsAtoms.get(i2);
        this.atoms.add(getList(aAtom, oAtom, oAtom2));
        if (this.atoms.size() >= MAX_ATOMS_SIZE) return;
      }

      // {avatar, score, observation}
      this.atoms.add(getList(aAtom, sAtom, oAtom));
      if (this.atoms.size() >= MAX_ATOMS_SIZE) return;

      if (counting) if (timer.remainingTimeMillis() < TIME_MARGIN) return;

      // {avatar, res, observation}
      for (long rAtom : resourceAtoms) {
        this.atoms.add(getList(aAtom, rAtom, oAtom));
        if (this.atoms.size() >= MAX_ATOMS_SIZE) return;
      }
    }

    // {avatar, resource, score}
    for (long rAtom : resourceAtoms) {
      this.atoms.add(getList(aAtom, rAtom, sAtom));
      if (this.atoms.size() >= MAX_ATOMS_SIZE) return;
    }

    // {avatar, score, observation}
    // {avatar, res, observation}
    // {avatar, object, object}
    // {avatar, resource, score}
    //TODO: Use these 4?
  }

  @Override
  boolean addAll(AtomSet as) {
    if (!(as instanceof Atom3SetReduced)) {
      return super.containsAll(as);
    }
    Atom3SetReduced a3sr = (Atom3SetReduced)as;
    return this.atoms.addAll(a3sr.atoms);
  }

  @Override
  boolean containsAll(AtomSet as) {
    if (!(as instanceof Atom3SetReduced)) {
      return super.containsAll(as);
    }
    Atom3SetReduced a3sr = (Atom3SetReduced)as;
    return this.atoms.containsAll(a3sr.atoms);
  }

  @Override
  int size() {
    return this.atoms.size();
  }

  private ArrayList<Long> getList(long i, long j, long k) {
    ArrayList<Long> aux = new ArrayList<>();
    aux.add(i);
    aux.add(j);
    aux.add(k);
    Collections.sort(aux);
    return aux;
  }
}

