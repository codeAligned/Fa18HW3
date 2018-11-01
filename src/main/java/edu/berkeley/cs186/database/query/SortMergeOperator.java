package edu.berkeley.cs186.database.query;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import edu.berkeley.cs186.database.Database;
import edu.berkeley.cs186.database.DatabaseException;
import edu.berkeley.cs186.database.databox.DataBox;
import edu.berkeley.cs186.database.io.Page;
import edu.berkeley.cs186.database.table.Record;
import edu.berkeley.cs186.database.table.RecordIterator;
import edu.berkeley.cs186.database.table.Schema;

public class SortMergeOperator extends JoinOperator {

  public SortMergeOperator(QueryOperator leftSource,
           QueryOperator rightSource,
           String leftColumnName,
           String rightColumnName,
           Database.Transaction transaction) throws QueryPlanException, DatabaseException {
    super(leftSource, rightSource, leftColumnName, rightColumnName, transaction, JoinType.SORTMERGE);

  }

  public Iterator<Record> iterator() throws QueryPlanException, DatabaseException {
    return new SortMergeOperator.SortMergeIterator();
  }

  public int estimateIOCost() throws QueryPlanException {
    //does nothing
    return 0;
  }   

  /**
   * An implementation of Iterator that provides an iterator interface for this operator.
   *
   * Before proceeding, you should read and understand SNLJOperator.java
   *    You can find it in the same directory as this file.
   *
   * Word of advice: try to decompose the problem into distinguishable sub-problems.
   *    This means you'll probably want to add more methods than those given (Once again,
   *    SNLJOperator.java might be a useful reference).
   * 
   */
  private class SortMergeIterator extends JoinIterator {
    /** 
    * Some member variables are provided for guidance, but there are many possible solutions.
    * You should implement the solution that's best for you, using any member variables you need.
    * You're free to use these member variables, but you're not obligated to.
    */

    private RecordIterator leftIterator;
    private RecordIterator rightIterator;
    private Record leftRecord;
    private Record nextRecord;
    private Record rightRecord;
    private boolean marked;
    private LR_RecordComparator recordComparator;

    public SortMergeIterator() throws QueryPlanException, DatabaseException {
      super();

      SortOperator leftSortOperator = new SortOperator(SortMergeOperator.this.getTransaction(), this.getLeftTableName(), new LeftRecordComparator());
      SortOperator rightSortOperator = new SortOperator(SortMergeOperator.this.getTransaction(), this.getRightTableName(), new RightRecordComparator());

      String sortedLeftTableName = leftSortOperator.sort();
      String sortedRightTableName = rightSortOperator.sort();

      this.leftIterator = SortMergeOperator.this.getRecordIterator(sortedLeftTableName);
      this.rightIterator = SortMergeOperator.this.getRecordIterator(sortedRightTableName);

      this.nextRecord = null;

      this.leftRecord = leftIterator.hasNext() ? leftIterator.next() : null;
      this.rightRecord = rightIterator.hasNext() ? rightIterator.next() : null;

      this.marked = false;
      this.recordComparator = new LR_RecordComparator();

      try {
        fetchNextRecord();
      } catch (DatabaseException e) {
        this.nextRecord = null;
      }
    }

    private void fetchNextRecord() throws DatabaseException {
      if (this.leftRecord == null) throw new DatabaseException("No new record to fetch");
      this.nextRecord = null;
      do {
        if (!this.marked) {
          if (this.leftRecord == null) {
            throw new DatabaseException("No new record to fetch");
          }
          while (this.leftRecord != null && this.recordComparator.compare(leftRecord, rightRecord) < 0) {
            this.leftRecord = this.leftIterator.hasNext() ? this.leftIterator.next() : null;
          }
          while (this.rightRecord != null && this.recordComparator.compare(leftRecord, rightRecord) > 0) {
            this.rightRecord = this.rightIterator.hasNext() ? this.rightIterator.next() : null;
          }
          this.marked = true;
          this.rightIterator.mark();
        }

        if (this.leftRecord != null && this.rightRecord != null && recordComparator.compare(leftRecord, rightRecord) == 0) {
          List<DataBox> leftValues = new ArrayList<>(this.leftRecord.getValues());
          List<DataBox> rightValues = new ArrayList<>(this.rightRecord.getValues());
          leftValues.addAll(rightValues);
          this.nextRecord = new Record(leftValues);
          this.rightRecord = this.rightIterator.hasNext() ? this.rightIterator.next() : null;
        }
        else {
         this.rightIterator.reset();
         this.rightRecord = this.rightIterator.hasNext() ? this.rightIterator.next() : null;
         this.leftRecord = this.leftIterator.hasNext() ? this.leftIterator.next() : null;
         this.marked = false;
        }
      } while (!hasNext());
    }


    /**
     * Checks if there are more record(s) to yield
     *
     * @return true if this iterator has another record to yield, otherwise false
     */
    public boolean hasNext() {
      return this.nextRecord != null;
    }

    /**
     * Yields the next record of this iterator.
     *
     * @return the next Record
     * @throws NoSuchElementException if there are no more Records to yield
     */
    public Record next() {
      if (!this.hasNext()) {
        throw new NoSuchElementException();
      }

      Record nextRecord = this.nextRecord;
      try {
        this.fetchNextRecord();
      } catch (DatabaseException e) {
        this.nextRecord = null;
      }
      return nextRecord;
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }


    private class LeftRecordComparator implements Comparator<Record> {
      public int compare(Record o1, Record o2) {
        return o1.getValues().get(SortMergeOperator.this.getLeftColumnIndex()).compareTo(
                o2.getValues().get(SortMergeOperator.this.getLeftColumnIndex()));
      }
    }

    private class RightRecordComparator implements Comparator<Record> {
      public int compare(Record o1, Record o2) {
        return o1.getValues().get(SortMergeOperator.this.getRightColumnIndex()).compareTo(
                o2.getValues().get(SortMergeOperator.this.getRightColumnIndex()));
      }
    }

    /**
    * Left-Right Record comparator
    * o1 : leftRecord
    * o2: rightRecord
    */
    private class LR_RecordComparator implements Comparator<Record> {
      public int compare(Record o1, Record o2) {
        return o1.getValues().get(SortMergeOperator.this.getLeftColumnIndex()).compareTo(
                o2.getValues().get(SortMergeOperator.this.getRightColumnIndex()));
      }
    }
  }
}