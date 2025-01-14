// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CharTable;
import com.intellij.util.ReflectionUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public final class CharTableImpl implements CharTable {
  private static final int INTERN_THRESHOLD = 40; // 40 or more characters long tokens won't be interned.

  private static final StringHashToCharSequencesMap STATIC_ENTRIES = newStaticSet();
  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  private final StringHashToCharSequencesMap entries = new StringHashToCharSequencesMap(10, 0.9f);

  @NotNull
  @Override
  public CharSequence intern(@NotNull CharSequence text) {
    return text.length() > INTERN_THRESHOLD ? text : doIntern(text);
  }

  @NotNull
  private CharSequence doIntern(@NotNull CharSequence text, int startOffset, int endOffset) {
    int hashCode = subSequenceHashCode(text, startOffset, endOffset);
    CharSequence interned;
    synchronized (STATIC_ENTRIES) {
      interned = STATIC_ENTRIES.getSubSequenceWithHashCode(hashCode, text, startOffset, endOffset);
    }
    if (interned != null) {
      return interned;
    }

    synchronized(entries) {
      return entries.getOrAddSubSequenceWithHashCode(hashCode, text, startOffset, endOffset);
    }
  }

  @NotNull
  public CharSequence doIntern(@NotNull CharSequence text) {
    return doIntern(text, 0, text.length());
  }

  @NotNull
  @Override
  public CharSequence intern(@NotNull CharSequence baseText, int startOffset, int endOffset) {
    CharSequence result;
    if (endOffset - startOffset == baseText.length()) result = intern(baseText);
    else if (endOffset - startOffset > INTERN_THRESHOLD) result = substring(baseText, startOffset, endOffset);
    else result = doIntern(baseText, startOffset, endOffset);

    return result;
  }

  @NotNull
  private static String substring(@NotNull CharSequence text, int startOffset, int endOffset) {
    if (text instanceof String) {
      return ((String)text).substring(startOffset, endOffset);
    }
    return text.subSequence(startOffset, endOffset).toString();
  }

  @Nullable
  public static CharSequence getStaticInterned(@NotNull CharSequence text) {
    return STATIC_ENTRIES.get(text);
  }

  public static void staticIntern(@NotNull String text) {
    synchronized(STATIC_ENTRIES) {
      STATIC_ENTRIES.add(text);
    }
  }

  @NotNull
  private static StringHashToCharSequencesMap newStaticSet() {
    StringHashToCharSequencesMap r = new StringHashToCharSequencesMap(10, 0.9f);
    r.add("==" );
    r.add("!=" );
    r.add("||" );
    r.add("++" );
    r.add("--" );

    r.add("<" );
    r.add("<=" );
    r.add("<<=" );
    r.add("<<" );
    r.add(">" );
    r.add("&" );
    r.add("&&" );

    r.add("+=" );
    r.add("-=" );
    r.add("*=" );
    r.add("/=" );
    r.add("&=" );
    r.add("|=" );
    r.add("^=" );
    r.add("%=" );

    r.add("("   );
    r.add(")"   );
    r.add("{"   );
    r.add("}"   );
    r.add("["   );
    r.add("]"   );
    r.add(";"   );
    r.add(","   );
    r.add("..." );
    r.add("."   );

    r.add("=" );
    r.add("!" );
    r.add("~" );
    r.add("?" );
    r.add(":" );
    r.add("+" );
    r.add("-" );
    r.add("*" );
    r.add("/" );
    r.add("|" );
    r.add("^" );
    r.add("%" );
    r.add("@" );

    r.add(" " );
    r.add("  " );
    r.add("   " );
    r.add("    " );
    r.add("     " );
    r.add("      " );
    r.add("       " );
    r.add("        " );
    r.add("         " );
    r.add("          " );
    r.add("           " );
    r.add("            " );
    r.add("             " );
    r.add("              " );
    r.add("               " );
    r.add("\n" );
    r.add("\n  " );
    r.add("\n    " );
    r.add("\n      " );
    r.add("\n        " );
    r.add("\n          " );
    r.add("\n            " );
    r.add("\n              " );
    r.add("\n                " );

    r.add("<");
    r.add(">");
    r.add("</");
    r.add("/>");
    r.add("\"");
    r.add("'");
    r.add("<![CDATA[");
    r.add("]]>");
    r.add("<!--");
    r.add("-->");
    r.add("<!DOCTYPE");
    r.add("SYSTEM");
    r.add("PUBLIC");
    r.add("<?");
    r.add("?>");

    r.add("<%");
    r.add("%>");
    r.add("<%=");
    r.add("<%@");
    r.add("${");
    r.add("");
    return r;
  }

  static {
    addStringsFromClassToStatics(CommonClassNames.class);
  }
  public static void addStringsFromClassToStatics(@NotNull Class<?> aClass) {
    for (Field field : aClass.getDeclaredFields()) {
      if ((field.getModifiers() & Modifier.STATIC) == 0) continue;
      if ((field.getModifiers() & Modifier.PUBLIC) == 0) continue;
      if (!String.class.equals(field.getType())) continue;
      String typeName = ReflectionUtil.getStaticFieldValue(aClass, String.class, field.getName());
      if (typeName != null) {
        staticIntern(typeName);
      }
    }
  }

  private static final class StringHashToCharSequencesMap extends Int2ObjectOpenHashMap<Object> {
    private StringHashToCharSequencesMap(int capacity, float loadFactor) {
      super(capacity, loadFactor);
    }

    private CharSequence get(@NotNull CharSequence sequence, int startOffset, int endOffset) {
      return getSubSequenceWithHashCode(subSequenceHashCode(sequence, startOffset, endOffset), sequence, startOffset, endOffset);
    }

    private CharSequence getSubSequenceWithHashCode(int hashCode, @NotNull CharSequence sequence, int startOffset, int endOffset) {
      Object o = get(hashCode);
      if (o == null) return null;
      if (o instanceof CharSequence) {
        if (charSequenceSubSequenceEquals((CharSequence)o, sequence, startOffset, endOffset)) {
          return (CharSequence)o;
        }
        return null;
      }
      if (o instanceof CharSequence[]) {
        for(CharSequence cs:(CharSequence[])o) {
          if (charSequenceSubSequenceEquals(cs, sequence, startOffset, endOffset)) {
            return cs;
          }
        }
        return null;
      }
      assert false:o.getClass();
      return null;
    }

    private static boolean charSequenceSubSequenceEquals(@NotNull CharSequence cs, @NotNull CharSequence baseSequence, int startOffset, int endOffset) {
      if (cs.length() != endOffset - startOffset) return false;
      if (cs == baseSequence && startOffset == 0) return true;
      for(int i = 0, len = cs.length(); i < len; ++i) {
        if (cs.charAt(i) != baseSequence.charAt(startOffset + i)) return false;
      }
      return true;
    }

    private CharSequence get(@NotNull CharSequence sequence) {
      return get(sequence, 0, sequence.length());
    }

    private void add(@NotNull CharSequence sequence) {
      int endOffset = sequence.length();
      int hashCode = subSequenceHashCode(sequence, 0, endOffset);
      getOrAddSubSequenceWithHashCode(hashCode, sequence, 0, endOffset);
    }

    @NotNull
    private CharSequence getOrAddSubSequenceWithHashCode(int hashCode, @NotNull CharSequence sequence, int startOffset, int endOffset) {
      Object value = get(hashCode);
      String addedSequence;

      if (value == null) {
        addedSequence = substring(sequence, startOffset, endOffset);
        put(hashCode, addedSequence);
      }
      else if (value instanceof CharSequence) {
        CharSequence existingSequence = (CharSequence)value;
        if (charSequenceSubSequenceEquals(existingSequence, sequence, startOffset, endOffset)) {
          return existingSequence;
        }
        addedSequence = substring(sequence, startOffset, endOffset);
        put(hashCode, new CharSequence[]{existingSequence, addedSequence});
      }
      else if (value instanceof CharSequence[]) {
        CharSequence[] existingSequenceArray = (CharSequence[])value;
        for (CharSequence cs : existingSequenceArray) {
          if (charSequenceSubSequenceEquals(cs, sequence, startOffset, endOffset)) {
            return cs;
          }
        }
        addedSequence = substring(sequence, startOffset, endOffset);
        CharSequence[] newSequenceArray = ArrayUtil.append(existingSequenceArray, addedSequence, CharSequence[]::new);
        put(hashCode, newSequenceArray);
      }
      else {
        assert false : value.getClass();
        return null;
      }
      return addedSequence;
    }
  }

  private static int subSequenceHashCode(@NotNull CharSequence sequence, int startOffset, int endOffset) {
    if (startOffset == 0 && endOffset == sequence.length()) {
      return StringUtil.stringHashCode(sequence);
    }
    return StringUtil.stringHashCode(sequence, startOffset, endOffset);
  }
}
