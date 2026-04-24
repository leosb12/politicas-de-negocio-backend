package com.leo.politicas_de_negocio.simulation.dto;

import com.leo.politicas_de_negocio.simulation.model.PolicyComparisonResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyComparisonResponse {

    private PolicyComparisonResult result;
}
