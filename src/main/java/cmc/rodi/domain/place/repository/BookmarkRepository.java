package cmc.rodi.domain.place.repository;

import cmc.rodi.domain.place.entity.Bookmark;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {}
