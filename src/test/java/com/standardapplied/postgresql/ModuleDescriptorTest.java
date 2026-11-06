/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package com.standardapplied.postgresql;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Module descriptor")
class ModuleDescriptorTest {

  @Test
  @DisplayName("public packages are exported, the vendored parser package is not")
  void shouldExportOnlyPublicPackages() {
    var module =
        ModuleFinder.of(Path.of("target", "classes"))
            .find("com.standardapplied.scimsql")
            .orElseThrow();

    Set<String> exports =
        module.descriptor().exports().stream()
            .map(java.lang.module.ModuleDescriptor.Exports::source)
            .collect(java.util.stream.Collectors.toSet());

    assertTrue(exports.contains("com.standardapplied.scimsql"));
    assertTrue(exports.contains("com.standardapplied.postgresql"));
    assertFalse(exports.contains("com.standardapplied.postgresql.parser"));
  }
}
