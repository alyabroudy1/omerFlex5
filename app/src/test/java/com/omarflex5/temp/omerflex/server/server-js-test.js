
                
  // mycima groupOfGroup
  document.addEventListener('DOMContentLoaded', () => {
var descElems = document.getElementsByClassName('StoryMovieContent');
                        var postList = [];
                        var desc = "";
                        console.log(descElems.length);
                        if(descElems.length > 0){
                        desc = descElems[0].textContent;
                        }
var boxs = document.getElementsByClassName('List--Seasons--Episodes');
if(boxs.length == 0){
                            //if (boxs.isEmpty()) => fetchGroup
                            // and return
}

    var box = boxs[0];
    var lis = box.getElementsByTagName('a');
     if(lis.length > 0){
                             for (let l = 0; l < lis.length; l++) {
                                var li = lis[l];
                                var post = {};

                                post.title = li.textContent;
                                post.videoUrl = li.getAttribute('href');
                                post.description = desc;
                                post.state = "+Movie.GROUP_STATE+";

                                // Clone 'movie' object
                                post.studio = "+ movie.getStudio() +" ;
                                post.fetch = "+ movie.getFetch() +";
                                post.cardImageUrl = "+ movie.getStudio()+";
                                post.backgroundImageUrl = "+ movie.getBackgroundImageUrl() +";
                                post.cardImageUrl = "+ movie.getCardImageUrl() +";
                                post.getMainMovieTitle = "+ movie.getMainMovieTitle() +";
                                postList.push(post);

                             }
                             MyJavaScriptInterface.myMethod(JSON.stringify(postList));
                          }
 });

  // mycima item
  document.addEventListener('DOMContentLoaded', () => {
var descElems = document.getElementsByClassName('StoryMovieContent');
                        var postList = [];
                        var desc = "";
                        console.log(descElems.length);
                        if(descElems.length > 0){
                        desc = descElems[0].textContent;
                        }else{
                                descElems = document.getElementsByClassName('PostItemContent');
                                if(descElems.length > 0){
                        desc = descElems[0].textContent;
                            }
                        }
                        // download links
var uls = document.getElementsByClassName('List--Download--Wecima--Single');
if(uls.length > 0){
   var box = uls[0];
       var lis = box.getElementsByTagName('li');
        if(lis.length > 0){
                                for (let l = 0; l < lis.length; l++) {
                                   var li = lis[l];
                                   var post = {};

                                   var videoLinkElems = li.getElementsByTagName('a');
                                   if(videoLinkElems.length > 0){
                                    var videoLinkElem = videoLinkElems[0];
                                    post.videoUrl = videoLinkElem.getAttribute('href');

                                    var titleElems = li.getElementsByTagName('resolution');
                                     if(titleElems.length > 0){
                                        var titleElem = titleElems[0];
                                         post.title = titleElem.textContent;
                                      }

                                      post.description = desc;
                                                                         post.state = "+Movie.RESOLUTION_STATE+";

                                                                         // Clone 'movie' object
                                                                         post.studio = "+ movie.getStudio() +" ;
                                                                         post.fetch = "+ movie.getFetch() +";
                                                                         post.cardImageUrl = "+ movie.getStudio()+";
                                                                         post.backgroundImageUrl = "+ movie.getBackgroundImageUrl() +";
                                                                         post.cardImageUrl = "+ movie.getCardImageUrl() +";
                                                                         post.getMainMovieTitle = "+ movie.getMainMovieTitle() +";
                                                                         postList.push(post);
                                 }



                                }
//                                postList;
                             }
}

                        // watch links
var ulsWatch = document.getElementsByClassName('WatchServersList');
if(ulsWatch.length > 0){
   var boxWatch = ulsWatch[0];
       var lisWatch = boxWatch.getElementsByTagName('btn');
        if(lisWatch.length > 0){
                                for (let l = 0; l < lisWatch.length; l++) {
                                   var li = lisWatch[l];
                                   var post = {};
                                    var videoUrl = li.getAttribute('data-url');

                                    if(videoUrl === ""){
                                    continue;
                                    }
                                    // todo: handle if the link already has headers like ?key
                                    post.videoUrl = videoUrl + "||referer=" + referer;


                                    var titleElems = li.getElementsByTagName('strong');
                                     if(titleElems.length > 0){
                                        var titleElem = titleElems[0];
                                         post.title = titleElem.textContent;
                                      }

                                      post.description = desc;
                                                                         post.state = "+Movie.BROWSER_STATE+";

                                                                         // Clone 'movie' object
                                                                         post.studio = "+ movie.getStudio() +" ;
                                                                         post.fetch = "+ movie.getFetch() +";
                                                                         post.cardImageUrl = "+ movie.getStudio()+";
                                                                         post.backgroundImageUrl = "+ movie.getBackgroundImageUrl() +";
                                                                         post.cardImageUrl = "+ movie.getCardImageUrl() +";
                                                                         post.getMainMovieTitle = "+ movie.getMainMovieTitle() +";
                                                                         postList.push(post);

                                }
//                                postList;
                             }
}

MyJavaScriptInterface.myMethod(JSON.stringify(postList));
 });



//delete all elements out of the body
var allElements = document.querySelectorAll('*'); +

                  // Iterate through all elements" +
                  for (var i = 0; i < allElements.length; i++) {
                    var element = allElements[i];

                    // Check if the element is outside the body element" +
                    if (!document.body.contains(element)) {
                      // If the element is outside the body, remove it" +
                      element.remove();
                    }
                  }