package backend.delegates.mainAdminProcess;

import backend.services.BoardService;
import backend.services.adminService.impl.AdminControlService;
import lombok.RequiredArgsConstructor;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class BlockingBoard implements JavaDelegate {
    private final BoardService boardService;
    private final AdminControlService adminService;

    @Override
    public void execute(DelegateExecution delegateExecution) throws Exception {
        long boardId = (long) delegateExecution.getVariable("boardId");

        if (boardService.findBoardById(boardId))
            adminService.blockUserBoard(boardId);
        else
            throw new Exception("доски с boardId = " + boardId + " не существует");

    }
}
