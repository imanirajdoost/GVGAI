package YBCriber;

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.TreeSet;

public class DoubleEndedPriorityQueue<E> extends AbstractQueue<E> implements Iterable<E>, Collection<E>, Queue<E> {
  private TreeSet<E> treeSet;

  public DoubleEndedPriorityQueue() {
    this(null);
  }

  public DoubleEndedPriorityQueue(Comparator<? super E> comp) {
    treeSet = new TreeSet<>(comp);
  }

  public Comparator<? super E> comparator() {
    return treeSet.comparator();
  }

  @Override
  public boolean contains(Object o) {
    return treeSet.contains(o);
  }

  @Override
  public Iterator<E> iterator() {
    return treeSet.iterator();
  }

  @Override
  public boolean offer(E e) {
    return treeSet.add(e);
  }

  @Override
  public E peek() {
    if (treeSet.isEmpty()) return null;
    return treeSet.first();
  }

  @Override
  public E poll() {
    E e = peek();
    if (e != null) treeSet.remove(e);
    return e;
  }

  @Override
  public boolean remove(Object o) {
    return treeSet.remove(o);
  }

  @Override
  public int size() {
    return treeSet.size();
  }

  @Override
  public Object[] toArray() {
    return treeSet.toArray();
  }

  // rear-end functionality

  public E elementLast() {
    E e = peekLast();
    if (e != null) return e;
    throw new NoSuchElementException();
  }

  public E peekLast() {
    if (treeSet.isEmpty()) return null;
    return treeSet.last();
  }

  public E pollLast() {
    E e = peekLast();
    if (e != null) treeSet.remove(e);
    return e;
  }

  public E removeLast() {
    E e = pollLast();
    if (e != null) return e;
    throw new NoSuchElementException();
  }
}

