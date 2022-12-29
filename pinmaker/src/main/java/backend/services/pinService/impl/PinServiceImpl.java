package backend.services.pinService.impl;

import backend.dto.requests.PinRequest;
import backend.dto.responses.PinWithPhotoResponse;
import backend.entities.Board;
import backend.entities.Pin;
import backend.entities.User;
import backend.exceptions.ApplicationException;
import backend.exceptions.ErrorEnum;
import backend.repositories.BoardRepository;
import backend.repositories.PinRepository;
import backend.repositories.UserRepository;
import backend.services.adminService.impl.AdminControlServiceImpl;
import backend.services.boardService.BoardService;
import backend.services.pinService.PinService;
import backend.services.userService.UserService;
import backend.utils.PhotoUtil;
import backend.utils.converters.DtoConvertor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class PinServiceImpl implements PinService {
    private final static String PATH_TO_PHOTO_BUFFER = "photoBuffer/";
    private final static String PATH_TO_PERMANENT_STORAGE = "userPhotos/";

    private final PinRepository pinRepository;
    private final BoardRepository boardRepository;
    private final UserRepository userRepository;

    private final UserService userService;
    private final BoardService boardService;
    private final AdminControlServiceImpl adminControlService;

    private final PhotoUtil photoIO;

    private PlatformTransactionManager transactionManager;

    private final DtoConvertor dtoConvertor;

    public void createPin(PinRequest pinRequest) throws Exception {

        if (boardService.findBoardById(pinRequest.getBoard_id(), pinRequest.getUserId()))
            throw ErrorEnum.OBJECT_DOES_NOT_EXIST.exception();

        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setName("pinTx");
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        TransactionStatus status = transactionManager.getTransaction(def);

        try {

            /**
             *  загружаем фотографию в постоянное хранилище и удаляем ее из буфера
             */

            byte[] photoByte = photoIO.getPhoto(PATH_TO_PHOTO_BUFFER, pinRequest.getFileName());
            photoIO.savePhoto(PATH_TO_PERMANENT_STORAGE, photoByte, pinRequest.getFileName());
            photoIO.deletePhoto(PATH_TO_PHOTO_BUFFER, pinRequest.getFileName());


            /**
             * загружаем пин в базу
             */


            Pin pin = dtoConvertor.convertPinDtoToEntity(pinRequest);

            Board board = boardRepository.
                    findBoardsByIdAndUser_Id(pinRequest.getBoard_id(), pinRequest.getUserId());

            User user = userRepository.findUserById(pinRequest.getUserId());

            pin.setBoard(board);
            pin.setUser(user);
            user.addPinToUser(pin);
            board.addPinToBoard(pin);

            try {
                pin = pinRepository.save(pin);
            } catch (Exception e) {
                log.error("Unexpected Error {}", e.getMessage());
                new ApplicationException(ErrorEnum.SERVICE_DATA_BASE_EXCEPTION.createApplicationError());
            }

            log.info("created new pin");

            /**
             * отправляем пин на проверку
             */

            adminControlService.sendPinToCheck(pin);

        } catch (Exception ex) {
            transactionManager.rollback(status);
            throw ex;
        }

        transactionManager.commit(status);
    }

    public List<PinWithPhotoResponse> findUserPins(Long id) {
        if (userService.findUser(id)) {
            return Collections.emptyList();
        }
        return pinToDTO(pinRepository.findAllByUser_Id(id));
    }


    public List<PinWithPhotoResponse> findBoardPins(Long id) {
        return pinToDTO(pinRepository.findAllByBoard_Id(id));
    }

    public boolean findPin(Long id) {
        return pinRepository.findPinById(id) != null;
    }



    private List<PinWithPhotoResponse> pinToDTO(List<Pin> pins) {

        List<PinWithPhotoResponse> photos = new ArrayList<>();
        for (Pin pin : pins) {
            PinWithPhotoResponse photo = new PinWithPhotoResponse();
            photo.setId(pin.getId());
            photo.setName(pin.getName());
            photo.setDescription(pin.getDescription());
            photo.setAltText(pin.getAltText());
            photo.setLink(pin.getLink());
            photo.setPhoto(pin.getOriginalFileName());
            photos.add(photo);
        }
        return photos;
    }


}
