package com.scratch.phd;

import java.util.function.Predicate;

public class SameIDAndModDatePredicate implements Predicate<DiffInfo>
{
   private DiffInfo diffInfo;

   public SameIDAndModDatePredicate(DiffInfo d)
   {
      this.diffInfo = d;
   }
   
   @Override
   public boolean test(DiffInfo d)
   {
      return d.id.equals(diffInfo.id) && d.lastModified.equals(diffInfo.lastModified);
   }
}