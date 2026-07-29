package com.smartirrigation.smartirrigation.service;

import com.smartirrigation.smartirrigation.dto.CulturaCreateRequestDTO;
import com.smartirrigation.smartirrigation.dto.CulturaDTO;
import com.smartirrigation.smartirrigation.exception.ConflictException;
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

    public CulturaDTO criar(CulturaCreateRequestDTO dto) {
        if (dto.getNome() == null || dto.getNome().isBlank()) {
            throw new IllegalArgumentException("O nome da cultura e obrigatorio.");
        }
        if (dto.getKc1() == null || dto.getKc2() == null || dto.getKc3() == null || dto.getKc4() == null) {
            throw new IllegalArgumentException("Os coeficientes Kc das 4 fases sao obrigatorios.");
        }

        String nome = dto.getNome().trim();
        if (culturaRepository.findByName(nome).isPresent()) {
            throw new ConflictException("Ja existe uma cultura chamada '" + nome + "'.");
        }

        CoeficienteCultura cultura = new CoeficienteCultura();
        cultura.setCultura(nome);
        cultura.setKc1(String.valueOf(dto.getKc1()));
        cultura.setKc2(String.valueOf(dto.getKc2()));
        cultura.setKc3(String.valueOf(dto.getKc3()));
        cultura.setKc4(String.valueOf(dto.getKc4()));
        culturaRepository.save(nome, cultura);

        return toDto(cultura);
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
