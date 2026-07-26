package com.smartirrigation.smartirrigation.service;

import com.smartirrigation.smartirrigation.dto.CulturaDTO;
import com.smartirrigation.smartirrigation.model.CoeficienteCultura;
import com.smartirrigation.smartirrigation.repository.CulturaRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CulturaServiceTest {

    private final CulturaRepository culturaRepository = mock(CulturaRepository.class);
    private final CulturaService service = new CulturaService(culturaRepository);

    @Test
    void listAll_mapsCoeficienteCulturaFieldsToDto() {
        CoeficienteCultura tomate = new CoeficienteCultura();
        tomate.setCultura("Tomate");
        tomate.setKc1("0.6");
        tomate.setKc2("0.8");
        tomate.setKc3("1.15");
        tomate.setKc4("0.9");
        when(culturaRepository.findAll()).thenReturn(List.of(tomate));

        List<CulturaDTO> result = service.listAll();

        assertEquals(1, result.size());
        assertEquals("Tomate", result.get(0).getNome());
        assertEquals(0.6, result.get(0).getKc1());
        assertEquals(0.8, result.get(0).getKc2());
        assertEquals(1.15, result.get(0).getKc3());
        assertEquals(0.9, result.get(0).getKc4());
    }

    @Test
    void listAll_returnsNullKc_whenValueIsBlankOrInvalid() {
        CoeficienteCultura semDados = new CoeficienteCultura();
        semDados.setCultura("SemDados");
        semDados.setKc1("");
        semDados.setKc2("abc");
        when(culturaRepository.findAll()).thenReturn(List.of(semDados));

        List<CulturaDTO> result = service.listAll();

        assertNull(result.get(0).getKc1());
        assertNull(result.get(0).getKc2());
    }
}
