/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.scimsql;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class Context {
  private final Map<String, List<Object>> params;
  private final Map<String, Object> indexedParams;
  private final Set<String> attributes;

  public Context() {
    this.params = new HashMap<>();
    this.indexedParams = new HashMap<>();
    this.attributes = new HashSet<>();
  }

  /**
   * Records that the filter uses an attribute, so the allowlist sees it before the clause is
   * rendered and whether or not it binds a parameter.
   */
  void reference(Filter attribute) {
    attributes.add(attribute.toString());
  }

  public String process(Filter attribute, Filter value, Function<String, String> keyMapper) {
    reference(attribute);
    var rawValue = value instanceof ValueFilter ? ((ValueFilter) value).value() : null;

    var key = attribute.toString();

    List<Object> values = params.computeIfAbsent(key, k -> new ArrayList<>());
    values.add(rawValue);

    var indexedKey = key.replace(".", "_") + values.size();
    indexedParams.put(indexedKey, rawValue);

    return keyMapper.apply(indexedKey);
  }

  public String processArray(
      Filter attribute, List<Filter> valueFilters, Function<String, String> keyMapper) {
    reference(attribute);
    var key = attribute.toString();
    List<Object> values = params.computeIfAbsent(key, k -> new ArrayList<>());
    var builder = new StringBuilder();

    for (var v = 0; v < valueFilters.size(); v++) {
      var vf = valueFilters.get(v);
      Object rawValue = null;
      ValueFilter vfv = null;

      if (vf instanceof ValueFilter valueFilter) {
        rawValue = valueFilter.value();
        vfv = valueFilter;
      }

      values.add(rawValue);

      var indexedKey = key.replace(".", "_") + values.size();
      indexedParams.put(indexedKey, rawValue);

      var paramKey = keyMapper.apply(indexedKey);
      if (vfv != null && vfv.isUuid()) {
        paramKey = "CAST(%s AS UUID)".formatted(paramKey);
      }
      builder.append(paramKey);

      if (v < valueFilters.size() - 1) {
        builder.append(", ");
      }
    }

    return builder.toString();
  }

  /**
   * Checks the filter against an allowlist of attributes.
   *
   * <p>Every attribute the filter uses must be listed: those in comparisons, in list checks and in
   * presence checks ({@code pr}). An attribute with its own alias is listed by its full path
   * ({@code u.userName}). For a filter parsed by {@link ScimEngine} the answer does not depend on
   * whether the clause has been rendered.
   *
   * @param validParamKeys the attributes callers may filter on
   * @return {@code true} if the filter uses only listed attributes
   */
  public boolean isValid(Set<String> validParamKeys) {
    for (String attribute : attributes) {
      if (!validParamKeys.contains(attribute)) {
        return false;
      }
    }
    return true;
  }

  public Map<String, Object> indexedParams() {
    return indexedParams;
  }

  public Map<String, List<Object>> params() {
    return params;
  }
}
