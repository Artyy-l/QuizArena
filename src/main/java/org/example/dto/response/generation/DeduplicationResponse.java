package org.example.dto.response.generation;

import org.example.dto.common.DuplicatePair;

import java.util.List;

public record DeduplicationResponse(
  Long questionSetId,
  Integer initialCount,
  Integer finalCount,
  List<DuplicatePair> duplicates
) {}
