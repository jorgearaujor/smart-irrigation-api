package com.smartirrigation.smartirrigation.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.smartirrigation.smartirrigation.dto.CriarUsuarioRequestDTO;
import com.smartirrigation.smartirrigation.dto.UsuarioResponseDTO;
import com.smartirrigation.smartirrigation.exception.ConflictException;
import com.smartirrigation.smartirrigation.exception.NotFoundException;
import com.smartirrigation.smartirrigation.model.Usuario;
import com.smartirrigation.smartirrigation.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UsuarioServiceTest {

    private final FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final UsuarioService service = new UsuarioService(firebaseAuth, usuarioRepository);

    @Test
    void criarUsuario_createsFirebaseUserAndSavesToFirestore() throws FirebaseAuthException {
        UserRecord userRecord = mock(UserRecord.class);
        when(userRecord.getUid()).thenReturn("uid-123");
        when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class))).thenReturn(userRecord);

        CriarUsuarioRequestDTO dto = new CriarUsuarioRequestDTO();
        dto.setNome("Fulano");
        dto.setEmail("fulano@example.com");
        dto.setSenha("senha123");
        dto.setTipo("Visualizador");

        UsuarioResponseDTO result = service.criarUsuario(dto);

        assertEquals("uid-123", result.getUid());
        assertEquals("Fulano", result.getNome());
        assertEquals("Visualizador", result.getTipo());

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(eq("uid-123"), captor.capture());
        assertEquals("fulano@example.com", captor.getValue().getEmail());
    }

    @Test
    void criarUsuario_throwsConflict_whenFirebaseAuthFails() throws FirebaseAuthException {
        when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class)))
                .thenThrow(mock(FirebaseAuthException.class));

        CriarUsuarioRequestDTO dto = new CriarUsuarioRequestDTO();
        dto.setNome("Fulano");
        dto.setEmail("ja-existe@example.com");
        dto.setSenha("senha123");
        dto.setTipo("Visualizador");

        assertThrows(ConflictException.class, () -> service.criarUsuario(dto));
    }

    @Test
    void getUsuarioAtual_returnsMappedProfile() {
        Usuario usuario = new Usuario();
        usuario.setUid("uid-456");
        usuario.setNome("Ciclana");
        usuario.setTipo("Administrador");
        when(usuarioRepository.findById("uid-456")).thenReturn(Optional.of(usuario));

        UsuarioResponseDTO result = service.getUsuarioAtual("uid-456");

        assertEquals("Ciclana", result.getNome());
        assertEquals("Administrador", result.getTipo());
    }

    @Test
    void getUsuarioAtual_throwsNotFound_whenMissing() {
        when(usuarioRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.getUsuarioAtual("missing"));
    }
}
