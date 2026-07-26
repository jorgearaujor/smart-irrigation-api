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
import org.springframework.stereotype.Service;

@Service
public class UsuarioService {

    private final FirebaseAuth firebaseAuth;
    private final UsuarioRepository usuarioRepository;

    public UsuarioService(FirebaseAuth firebaseAuth, UsuarioRepository usuarioRepository) {
        this.firebaseAuth = firebaseAuth;
        this.usuarioRepository = usuarioRepository;
    }

    public UsuarioResponseDTO criarUsuario(CriarUsuarioRequestDTO dto) {
        UserRecord.CreateRequest request = new UserRecord.CreateRequest()
                .setEmail(dto.getEmail())
                .setPassword(dto.getSenha())
                .setDisplayName(dto.getNome());

        UserRecord userRecord;
        try {
            userRecord = firebaseAuth.createUser(request);
        } catch (FirebaseAuthException e) {
            throw new ConflictException("Nao foi possivel criar o usuario: " + e.getMessage());
        }

        Usuario usuario = new Usuario();
        usuario.setUid(userRecord.getUid());
        usuario.setNome(dto.getNome());
        usuario.setEmail(dto.getEmail());
        usuario.setTipo(dto.getTipo());
        usuario.setUrlImg("");
        usuarioRepository.save(userRecord.getUid(), usuario);

        return toDto(usuario);
    }

    public UsuarioResponseDTO getUsuarioAtual(String uid) {
        Usuario usuario = usuarioRepository.findById(uid)
                .orElseThrow(() -> new NotFoundException("Usuario nao encontrado: " + uid));
        return toDto(usuario);
    }

    private static UsuarioResponseDTO toDto(Usuario usuario) {
        UsuarioResponseDTO dto = new UsuarioResponseDTO();
        dto.setUid(usuario.getUid());
        dto.setNome(usuario.getNome());
        dto.setEmail(usuario.getEmail());
        dto.setTipo(usuario.getTipo());
        dto.setUrlImg(usuario.getUrlImg());
        return dto;
    }
}
