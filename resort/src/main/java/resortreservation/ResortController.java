package resortreservation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

 @RestController
 public class ResortController {

    private ResortRepository repository;

    public ResortController(ResortRepository repository){
        this.repository = repository;
    }

    @RequestMapping(method= RequestMethod.GET, value="/resorts/{id}")
        public Resort getResortStatus(@PathVariable("id") Long id){

            //hystix test code
            // try {
            //     Thread.currentThread().sleep((long) (400 + Math.random() * 220));
            // } catch (InterruptedException e) { }

            return repository.findById(id).get();
        }




 }
