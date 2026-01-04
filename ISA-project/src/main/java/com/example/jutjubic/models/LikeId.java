package com.example.jutjubic.models;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
@EqualsAndHashCode
@NoArgsConstructor @AllArgsConstructor
@Getter @Setter
public class LikeId implements Serializable {
    @Serial
    private static final long serialVersionUID = -1345824712982636256L;

    @NotNull
    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID userId;

    @NotNull
    @Column(name = "video_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID videoId;
}