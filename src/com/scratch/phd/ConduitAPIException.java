package com.scratch.phd;

@SuppressWarnings("serial")
public class ConduitAPIException extends Exception
{
   public final int code;

   public ConduitAPIException(String message)
   {
      super(message);
      this.code = 0;
   }

   public ConduitAPIException(String message, int code)
   {
      super(message);
      this.code = code;
   }
}