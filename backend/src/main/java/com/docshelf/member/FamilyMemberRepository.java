// Spring Data repository for family_member
package com.docshelf.member;

import com.docshelf.member.entity.FamilyMember;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilyMemberRepository extends JpaRepository<FamilyMember, UUID> {

    Optional<FamilyMember> findByIsSelfTrue();

    List<FamilyMember> findAllByOrderByFullNameAsc();

    Optional<FamilyMember> findByPanHmac(String panHmac);
}
