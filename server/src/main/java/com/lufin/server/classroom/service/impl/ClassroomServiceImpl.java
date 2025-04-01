package com.lufin.server.classroom.service.impl;

import static com.lufin.server.common.constants.ErrorCode.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lufin.server.account.domain.Account;
import com.lufin.server.account.service.AccountService;
import com.lufin.server.classroom.domain.Classroom;
import com.lufin.server.classroom.domain.MemberClassroom;
import com.lufin.server.classroom.dto.ClassCodeRequest;
import com.lufin.server.classroom.dto.ClassCodeResponse;
import com.lufin.server.classroom.dto.ClassRequest;
import com.lufin.server.classroom.dto.ClassResponse;
import com.lufin.server.classroom.dto.FindClassesResponse;
import com.lufin.server.classroom.dto.LoginWithClassResponse;
import com.lufin.server.classroom.dto.UpdateClassRequest;
import com.lufin.server.classroom.factory.ResponseFactory;
import com.lufin.server.classroom.repository.ClassroomRepository;
import com.lufin.server.classroom.repository.MemberClassroomRepository;
import com.lufin.server.classroom.service.ClassroomService;
import com.lufin.server.classroom.util.ClassCodeGenerator;
import com.lufin.server.common.exception.BusinessException;
import com.lufin.server.member.domain.Member;
import com.lufin.server.member.domain.MemberRole;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClassroomServiceImpl implements ClassroomService {

	private final ClassroomRepository classroomRepository;
	private final MemberClassroomRepository memberClassroomRepository;
	private final AccountService accountService;
	private final ResponseFactory responseFactory;

	@Transactional
	@Override
	public LoginWithClassResponse createClassroom(ClassRequest request, Member currentMember) {

		log.info("[클래스 생성 시도] 요청자: {}", currentMember);
		Member teacher = memberAuthorization(currentMember);

		// 동일한 교사가 같은 해, 같은 학교, 같은 학년, 같은 반 번호로 클래스 생성 시 중복
		checkDuplicateClassroom(request.school(), request.grade(), request.classGroup(), teacher);

		// 클래스 코드 생성
		String classCode = generateUniqueClassCode();
		log.debug("[클래스 코드 생성] {}", classCode);

		Classroom newClass = Classroom.create(
			teacher,
			classCode,
			request.school(),
			request.grade(),
			request.classGroup(),
			request.name(),
			request.key()
		);

		classroomRepository.save(newClass);
		log.info("[클래스 저장 완료] classId: {}, code: {}", newClass.getId(), newClass.getCode());

		// 클래스 계좌 생성
		Account account = accountService.createAccountForClassroom(newClass);
		log.info("[클래스 계좌 생성 완료] accountId: {}", account.getId());

		// 기존에 소속된 클래스(isCurrent=true)가 있다면 deactivate()
		deactivateIfInActiveClass(teacher);

		// 교사를 클래스에 매핑
		MemberClassroom addTeacher = MemberClassroom.enroll(teacher, newClass);
		memberClassroomRepository.save(addTeacher);
		log.info("[교사 클래스 매핑 완료] 교사: {}, 클래스: {}", teacher.getName(), newClass.getName());

		return responseFactory.createLoginWithClassResponse(teacher, newClass, account);
	}

	@Transactional(readOnly = true)
	@Override
	public List<FindClassesResponse> findClasses(int memberId) {
		log.info("[클래스 이력 조회 요청] memberId: {}", memberId);

		// 특정 회원이 소속된 모든 학급 이력 조회
		List<MemberClassroom> memberClassrooms = memberClassroomRepository.findByMember_Id(memberId);
		log.debug("[조회된 클래스 수] {}", memberClassrooms.size());

		// 각 학급 정보를 DTO로 변환하여 반환
		return memberClassrooms.stream()
			.map(mc -> {
				Classroom classroom = mc.getClassroom();

				// 현재 학급에 속한 전체 인원 수 조회
				int memberCount = memberClassroomRepository.countByClassroom_Id(classroom.getId());

				// 현재 학급

				return new FindClassesResponse(
					classroom.getName(),
					classroom.getSchool(),
					classroom.getCreatedAt().getYear(),
					classroom.getGrade(),
					classroom.getClassGroup(),
					memberCount,
					classroom.getThumbnailKey()
				);
			})
			.toList(); // 스트림을 리스트로 변환하여 반환
	}

	@Transactional(readOnly = true)
	@Override
	public FindClassesResponse findCurrentClass(int memberId) {
		log.info("[현재 소속 클래스 조회 요청] memberId: {}", memberId);

		Optional<MemberClassroom> currentClassroom =
			memberClassroomRepository.findByMember_IdAndIsCurrentTrue(memberId);

		if (currentClassroom.isPresent()) {
			Classroom classroom = currentClassroom.get().getClassroom();
			int memberCount = memberClassroomRepository.countByClassroom_Id(classroom.getId());

			log.info("[현재 클래스 조회 성공] classId: {}, className: {}", classroom.getId(), classroom.getName());

			return new FindClassesResponse(
				classroom.getName(),
				classroom.getSchool(),
				classroom.getCreatedAt().getYear(),
				classroom.getGrade(),
				classroom.getClassGroup(),
				memberCount,
				classroom.getThumbnailKey()
			);
		}
		log.warn("🏫[현재 소속 클래스 없음] memberId: {}", memberId);
		throw new BusinessException(CLASS_NOT_FOUND);
	}

	@Transactional(readOnly = true)
	@Override
	public ClassCodeResponse findClassCode(Member teacher) {
		log.info("[클래스 코드 조회 요청] teacher: {}", teacher);

		Member currentMember = memberAuthorization(teacher);

		return memberClassroomRepository
			.findByMember_IdAndIsCurrentTrue(currentMember.getId())
			.map(memberClassroom -> new ClassCodeResponse(memberClassroom.getClassroom().getCode()))
			.orElseThrow(() -> {
				log.warn("🏫[현재 소속된 클래스 없음]: {}", teacher);
				return new BusinessException(CLASS_NOT_FOUND);
			});
	}

	@Transactional
	@Override
	public LoginWithClassResponse enrollClass(Member member, ClassCodeRequest request) {
		log.info("[클래스 등록 요청] memberId: {}, classCode: {}", member.getId(), request.code());

		// 코드로 조회되는 반이 있나요?
		Classroom classroom = classroomRepository.findByCode(request.code())
			.orElseThrow(() -> {
				log.warn("🏫[클래스 코드 미존재] code: {}", request.code());
				return new BusinessException(CLASS_NOT_FOUND);
			});

		// 기존에 소속된 클래스(isCurrent=true)가 있다면 deactivate()
		deactivateIfInActiveClass(member);

		// 클래스에 멤버 저장 후 계좌 개설
		MemberClassroom.enroll(member, classroom);
		log.info("[학생 클래스 매핑 완료] memberId: {}, classId: {}", member.getId(), classroom.getId());

		Account account = accountService.createAccountForMember(member.getId());
		log.info("[학생 계좌 생성 완료] memberId: {}, accountId: {}", member.getId(), account.getId());

		// 토큰 발급
		return responseFactory.createLoginWithClassResponse(member, classroom, account);
	}

	@Transactional
	@Override
	public ClassResponse updateClassroom(Member member, UpdateClassRequest request) {
		log.info("[클래스 정보 수정 요청] 요청자: {}", member);

		Member teacher = memberAuthorization(member);

		// 현재 소속 클래스 조회
		MemberClassroom current = memberClassroomRepository
			.findByMember_IdAndIsCurrentTrue(teacher.getId())
			.orElseThrow(() -> {
				log.warn("🏫[수정 실패 - 소속 클래스 없음] teacherId: {}", teacher.getId());
				return new BusinessException(CLASS_NOT_FOUND);
			});

		Classroom classroom = current.getClassroom();

		// 수정하려는 정보로 중복 체크 (학교, 학년, 반 번호 + 연도 + 교사)
		checkDuplicateClassroom(request.school(), request.grade(), request.classGroup(), teacher);
		log.debug("[중복 체크 완료] 수정할 classId: {}", classroom.getId());

		// 엔티티 내부에서 수정 메서드 호출
		classroom.updateInfo(
			request.school(),
			request.grade(),
			request.classGroup(),
			request.name(),
			request.key()
		);
		log.info("[클래스 정보 수정 완료] classId: {}", classroom.getId());

		Account account = accountService.createAccountForMember(member.getId());

		return new ClassResponse(
			classroom.getId(),
			classroom.getName(),
			classroom.getSchool(),
			classroom.getCreatedAt().getYear(),
			classroom.getGrade(),
			classroom.getClassGroup(),
			classroom.getCode(),
			account.getBalance()
		);
	}

	@Transactional
	@Override
	public void deleteClassroom(Member member, int classId) {
		log.info("[클래스 삭제 시도] 요청자: {}, classId: {}", member.getId(), classId);

		Member teacher = memberAuthorization(member);

		// 교사가 해당 클래스에 소속되어 있는지 확인
		boolean existsClassroom = memberClassroomRepository
			.existsByMember_IdAndClassroom_Id(teacher.getId(), classId);
		if (!existsClassroom) {
			log.warn("🏫[삭제 실패 - 소속된 클래스 아님] teacherId: {}, classId: {}", teacher.getId(), classId);
			throw new BusinessException(CLASS_NOT_FOUND);
		}

		// 교사가 만든 클래스인지 확인
		Classroom classroom = classroomRepository.findByIdAndTeacher_Id(classId, teacher.getId())
			.orElseThrow(() -> {
				log.warn("🏫[삭제 실패 - 본인이 생성한 클래스 아님] classId: {}", classId);
				return new BusinessException(CLASS_NOT_FOUND);
			});

		// 다른 멤버가 존재하면 삭제 불가 (본인 포함 2명이면 1명만 존재)
		int memberCount = memberClassroomRepository.countByClassroom_Id(classroom.getId());
		if (memberCount > 2) {
			log.warn("🏫[삭제 실패 - 학생 존재] classId: {}, 멤버 수: {}", classId, memberCount);
			throw new BusinessException(CLASS_HAS_STUDENTS);
		}

		// 연관 관계 먼저 삭제 (MemberClassroom)
		memberClassroomRepository.deleteAllByClassroom(classroom);
		log.info("[멤버-클래스 관계 삭제 완료] classId: {}", classId);

		classroomRepository.delete(classroom);
		log.info("[클래스 삭제 완료] classId: {}", classId);
	}

	private void checkDuplicateClassroom(String school, int grade, int classGroup, Member teacher) {
		int year = LocalDate.now().getYear();
		boolean exist = classroomRepository.existsDuplicateClassroom(school, grade,
			classGroup, year, teacher.getId());
		if (exist) {
			log.warn("🏫[클래스 중복 발생] 동일한 조건의 클래스 존재 - teacherId: {}, year: {}", teacher.getId(), year);
			throw new BusinessException(DUPLICATE_CLASSROOM);
		}
	}

	private Member memberAuthorization(Member currentMember) {
		if (currentMember == null) {
			log.warn("🏫[권한 확인 실패] 요청한 사용자 정보 없음 (null)");
			throw new BusinessException(UNAUTHORIZED_ACCESS);
		}
		if (currentMember.getMemberRole() != MemberRole.TEACHER) {
			log.warn("🏫[권한 확인 실패] 교사가 아님 - memberId: {}, role: {}", currentMember.getId(),
				currentMember.getMemberRole());
			throw new BusinessException(REQUEST_DENIED);
		}
		return currentMember;
	}

	private String generateUniqueClassCode() {
		String classCode;
		do {
			classCode = ClassCodeGenerator.generateClassCode();
		} while (classroomRepository.findByCode(classCode).isPresent());
		return classCode;
	}

	private void deactivateIfInActiveClass(Member teacher) {
		Optional<MemberClassroom> hasCurrentClassroom = memberClassroomRepository.findByMember_IdAndIsCurrentTrue(
			teacher.getId());

		if (hasCurrentClassroom.isPresent()) {
			hasCurrentClassroom.get().deactivate();
			memberClassroomRepository.save(hasCurrentClassroom.get());
		}
	}
}
