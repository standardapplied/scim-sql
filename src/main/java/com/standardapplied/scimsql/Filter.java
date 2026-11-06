/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package com.standardapplied.scimsql;

public interface Filter {

  String toClause();

  Context context();

  static String camelToSnake(String camelCase) {
    if (camelCase == null || camelCase.isEmpty()) {
      return camelCase;
    }

    StringBuilder result = new StringBuilder();
    char firstChar = camelCase.charAt(0);
    // Add first character in lower case
    result.append(Character.toLowerCase(firstChar));

    // Process rest of the string
    for (int i = 1; i < camelCase.length(); i++) {
      char currentChar = camelCase.charAt(i);

      if (Character.isUpperCase(currentChar)) {
        result.append('_');
        result.append(Character.toLowerCase(currentChar));
      } else {
        result.append(currentChar);
      }
    }

    return result.toString();
  }
}
