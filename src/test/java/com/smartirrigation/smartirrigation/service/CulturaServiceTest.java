package com.smartirrigation.smartirrigation.service;

import com.smartirrigation.smartirrigation.dto.CulturaCreateRequestDTO;
import com.smartirrigation.smartirrigation.dto.CulturaDTO;
import com.smartirrigation.smartirrigation.exception.ConflictException;
import com.smartirrigation.smartirrigation.model.CoeficienteCultura;
import com.smartirrigation.smartirrigation.repository.CulturaRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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

    @Test
    void criar_throwsIllegalArgument_whenNomeIsBlank() {
        CulturaCreateRequestDTO dto = new CulturaCreateRequestDTO();
        dto.setNome("  ");
        dto.setKc1(0.5);
        dto.setKc2(0.7);
        dto.setKc3(1.0);
        dto.setKc4(0.8);

        assertThrows(IllegalArgumentException.class, () -> service.criar(dto));
    }

    @Test
    void criar_throwsIllegalArgument_whenKcIsMissing() {
        CulturaCreateRequestDTO dto = new CulturaCreateRequestDTO();
        dto.setNome("Alface");
        dto.setKc1(0.5);
        dto.setKc2(null);
        dto.setKc3(1.0);
        dto.setKc4(0.8);

        assertThrows(IllegalArgumentException.class, () -> service.criar(dto));
    }

    @Test
    void criar_throwsConflict_whenCulturaJaExiste() {
        CulturaCreateRequestDTO dto = new CulturaCreateRequestDTO();
        dto.setNome("Tomate");
        dto.setKc1(0.6);
        dto.setKc2(0.8);
        dto.setKc3(1.15);
        dto.setKc4(0.9);
        when(culturaRepository.findByName("Tomate")).thenReturn(Optional.of(new CoeficienteCultura()));

        assertThrows(ConflictException.class, () -> service.criar(dto));
    }

    @Test
    void criar_savesAndReturnsDto_whenValido() {
        CulturaCreateRequestDTO dto = new CulturaCreateRequestDTO();
        dto.setNome("Alface");
        dto.setKc1(0.5);
        dto.setKc2(0.7);
        dto.setKc3(1.0);
        dto.setKc4(0.8);
        when(culturaRepository.findByName("Alface")).thenReturn(Optional.empty());

        CulturaDTO result = service.criar(dto);

        assertEquals("Alface", result.getNome());
        assertEquals(0.5, result.getKc1());
        assertEquals(0.7, result.getKc2());
        assertEquals(1.0, result.getKc3());
        assertEquals(0.8, result.getKc4());

        ArgumentCaptor<CoeficienteCultura> captor = ArgumentCaptor.forClass(CoeficienteCultura.class);
        verify(culturaRepository).save(eq("Alface"), captor.capture());
        assertEquals("0.5", captor.getValue().getKc1());
    }

    @Test
    void criar_throwsIllegalArgument_whenKcIsNegative() {
        CulturaCreateRequestDTO dto = new CulturaCreateRequestDTO();
        dto.setNome("Milho");
        dto.setKc1(-0.5);
        dto.setKc2(0.7);
        dto.setKc3(1.0);
        dto.setKc4(0.8);

        assertThrows(IllegalArgumentException.class, () -> service.criar(dto));
    }

    @Test
    void criar_throwsIllegalArgument_whenKcIsTooLarge() {
        CulturaCreateRequestDTO dto = new CulturaCreateRequestDTO();
        dto.setNome("Soja");
        dto.setKc1(0.5);
        dto.setKc2(5.0);
        dto.setKc3(1.0);
        dto.setKc4(0.8);

        assertThrows(IllegalArgumentException.class, () -> service.criar(dto));
    }
}
