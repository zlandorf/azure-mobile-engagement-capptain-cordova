/*
 * Copyright 2014 Capptain
 * 
 * Licensed under the CAPPTAIN SDK LICENSE (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *   https://app.capptain.com/#tos
 *  
 * This file is supplied "as-is." You bear the risk of using it.
 * Capptain gives no express or implied warranties, guarantees or conditions.
 * You may have additional consumer rights under your local laws which this agreement cannot change.
 * To the extent permitted under your local laws, Capptain excludes the implied warranties of merchantability,
 * fitness for a particular purpose and non-infringement.
 */

package com.ubikod.capptain.android.sdk;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;

/** Crash identifier */
class CrashId
{
  /** Packages that are not considered the origin of an exception in the stack traces */
  /* @formatter:off */
  private static final String[] STACK_TRACE_PACKAGE_SKIP_LIST = {
    "android",
    "com.android",
    "dalvik",
    "java.lang.reflect"
  };
  /* @formatter:on */

  /** Crash type */
  private final Class<?> type;

  /** Crash location class */
  private final String locationClass;

  /** Crash location method */
  private final String locationMethod;

  /**
   * Init crash identifier.
   * @param type crash type.
   * @param location crash location (origin of the crash in the stack trace).
   */
  private CrashId(Class<?> type, StackTraceElement location)
  {
    this(type, location.getClassName(), location.getMethodName());
  }

  /**
   * Init crash identifier.
   * @param type crash type.
   * @param locationClass origin class of the crash.
   * @param locationMethod origin method of the crash.
   */
  private CrashId(Class<?> type, String locationClass, String locationMethod)
  {
    this.type = type;
    this.locationClass = locationClass;
    this.locationMethod = locationMethod;
  }

  /**
   * Get crash type.
   * @return crash type.
   */
  public String getType()
  {
    return type.getName();
  }

  /**
   * Get crash location as a string.
   * @return crash location as a string.
   */
  public String getLocation()
  {
    /*
     * We don't use line number because it can be variable for the same semantic crash: different
     * Android versions, different application versions with the same crash not being fixed (but
     * with origin source file being modified).
     */
    if (locationClass == null)
      return null;
    else
      return locationClass + "." + locationMethod;
  }

  /**
   * Get crash identifier from the specified throwable.
   * @param ex throwable.
   * @return crash identifier.
   */
  public static CrashId from(Context context, Throwable ex)
  {
    /*
     * Loop on causes to determine exception type to use and class/method to use for crashid
     * computation. The class.method used as the location of a crash (the origin of the crash) is
     * never considered to be an Android method, try to find an application method as being the
     * origin of the crash. If we don't find one, use the first method as origin. OutOfMemoryError
     * is special: it can happen pretty much everywhere, use no origin for this one: aggregate all
     * OutOfMemoryError into one crash identifier (whatever its position in the causal chain).
     */
    Class<? extends Throwable> topClassName = ex.getClass();
    for (Throwable cause = ex; cause != null; cause = cause.getCause())
      if (cause.getClass().equals(OutOfMemoryError.class))
        return new CrashId(OutOfMemoryError.class, null, null);
      else
        for (StackTraceElement line : cause.getStackTrace())
          if (!isAndroidLine(line))
            return new CrashId(topClassName, line);

    /*
     * Very specific case: RuntimeException thrown by ActivityThread. There is no hint of
     * application code in the stack trace lines, only the message can be parsed. Keep original
     * method name but change ActivityThread by the application class name found in the message.
     */
    StackTraceElement firstLine = ex.getStackTrace()[0];
    if (ex instanceof RuntimeException
      && "android.app.ActivityThread".equals(firstLine.getClassName()))
    {
      /* Try parsing message for class name */
      Pattern pattern = Pattern.compile("\\{" + context.getPackageName() + "/([^\\}]+)");
      Matcher matcher = pattern.matcher(ex.getMessage());
      if (matcher.find())
        return new CrashId(topClassName, matcher.group(1), firstLine.getMethodName());
    }

    /* Fail over first type and line if no smart origin could be found */
    return new CrashId(topClassName, firstLine);
  }

  /**
   * Check whether a stack trace line is part of the Android source code, or generated by Android
   * source code.
   * @param line stack trace line.
   * @return true if part of Android source code, false otherwise.
   */
  private static boolean isAndroidLine(StackTraceElement line)
  {
    for (String prefix : STACK_TRACE_PACKAGE_SKIP_LIST)
      if (line.getClassName().startsWith(prefix + "."))
        return true;
    return false;
  }
}
