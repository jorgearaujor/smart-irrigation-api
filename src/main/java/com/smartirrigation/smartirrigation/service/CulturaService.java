package com.smartirrigation.smartirrigation.service;

import com.smartirrigation.smartirrigation.dto.CulturaDTO;
import com.smartirrigation.smartirrigation.model.CoeficienteCultura;
import com.smartirrigation.smartirrigation.repository.CulturaRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CulturaService {

    private final CulturaRepository culturaRepository;

    public CulturaService(CulturaRepository culturaRepository) {
        this.culturaRepository = culturaRepository;
    }

    public List<CulturaDTO> listAll() {
        return culturaRepository.findAll().stream()
                .map(CulturaService::toDto)
                .collect(Collectors.toList());
    }

    private static CulturaDTO toDto(CoeficienteCultura cultura) {
        CulturaDTO dto = new CulturaDTO();
        dto.setNome(cultura.getCultura());
        dto.setKc1(parseKc(cultura.getKc1()));
        dto.setKc2(parseKc(cultura.getKc2()));
        dto.setKc3(parseKc(cultura.getKc3()));
        dto.setKc4(parseKc(cultura.getKc4()));
        return dto;
    }

    private static Double parseKc(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value.replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
