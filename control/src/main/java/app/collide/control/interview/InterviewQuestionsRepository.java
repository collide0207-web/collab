package app.collide.control.interview;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewQuestionsRepository extends JpaRepository<InterviewQuestions, UUID> {
}
