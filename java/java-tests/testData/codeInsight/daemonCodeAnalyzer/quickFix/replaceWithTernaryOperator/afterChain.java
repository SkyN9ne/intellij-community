// "Replace with 'integer != null ?:'" "true-preview"

import java.lang.Integer;

class A{
  void test(){
    Integer integer = null;
    int i = integer != null ? integer.toString().length() : 0<caret>;
  }
}