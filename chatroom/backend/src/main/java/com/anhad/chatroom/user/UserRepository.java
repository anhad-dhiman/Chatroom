package com.anhad.chatroom.user;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    @Query("""
            select u from User u
            where u.id <> :excludeId
              and (lower(u.username) like lower(concat('%', :query, '%'))
                   or lower(u.displayName) like lower(concat('%', :query, '%')))
            order by u.username
            """)
    List<User> search(@Param("query") String query, @Param("excludeId") Long excludeId, Pageable pageable);
}
