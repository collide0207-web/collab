package app.collide.control.link;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShareLinkRepository extends JpaRepository<ShareLink, UUID> {
    Optional<ShareLink> findByTokenHash(String tokenHash);
}
