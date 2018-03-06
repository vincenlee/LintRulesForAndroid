package com.ytjojo.news;

import com.easygroup.ngaridoctor.library.Doctor;
import io.reactivex.Observable;
import retrofit2.http.ArrayItem;
import retrofit2.http.NgariJsonPost;
import retrofit2.http.POST;

public interface HttpService {


	@POST("*.jsonRequest")
	@NgariJsonPost(serviceId = "eh.rankingInfoService", method = "rankThumbsUp")
	Observable<Doctor> getPatient(@ArrayItem String patientFeedback);


}